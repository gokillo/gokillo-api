/*#
  * @file pledges.scala
  * @begin 20-Nov-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent._
import scala.util.control.NonFatal
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import services.common._
import services.common.CommonErrors._
import models.common.{Address, RefId, State}
import models.common.Address._
import models.core._
import models.pay._

package object pledges {

  /** Implicit FSM used by JSON state serializer/deserializer. */
  implicit val fsm = PledgeFsm

  /**
    * Implements a finite state machine (FSM) for transducing pledge states.
    */
  object PledgeFsm extends FsmBase {

    trait Message extends FsmMessage { def projectId: String }

    // FSM messages
    case class Save(pledge: Pledge) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class List(projectId: String, page: Int, perPage: Int) extends Message
    case class ListByState(projectId: String, page: Int, perPage: Int) extends Message
    case class Grant(projectId: String) extends Message
    case class Revoke(projectId: String) extends Message
    case class CashIn(projectId: String) extends Message
    case class Reward(projectId: String, pledgeId: String) extends Message
    case class Refund(projectId: String, accountId: String, coinAddress: String) extends Message
    case class SetShippingInfo(projectId: String, shippingInfo: ShippingInfo) extends Message
    case class ReturnCoins(transaction: Transaction) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class TakeUpAwaitingRefundThreshold() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class PollRevoked() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class PullUnclaimed() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }

    private[PledgeFsm] class Val(name: String, convertWith: FsmTransduction) extends super.Val(name, convertWith) {

      def !(message: Save) = convertWith.asInstanceOf[Transduction](message)
      def !(message: List) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListByState) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Grant) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Revoke) = convertWith.asInstanceOf[Transduction](message)
      def !(message: CashIn) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Reward) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Refund) = convertWith.asInstanceOf[Transduction](message)
      def !(message: SetShippingInfo) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ReturnCoins) = convertWith.asInstanceOf[Transduction](message)
      def !(message: TakeUpAwaitingRefundThreshold) = convertWith.asInstanceOf[Transduction](message)
      def !(message: PollRevoked) = convertWith.asInstanceOf[Transduction](message)
      def !(message: PullUnclaimed) = convertWith.asInstanceOf[Transduction](message)
    }

    protected def Val(name: String, convertWith: FsmTransduction) = new Val(name, convertWith)

    /**
      * Implicitly converts the specified state value to a `Val`.
      *
      * @param value  The state value to convert.
      * @return       The `Val` converted from `value`.
      */
    implicit def toVal(value: Value) = value.asInstanceOf[Val]

    /** The pledge has just been created. */
    val New = Value("new", new FromNew)

    /** The pledge has been settled. */
    val Settled = Value("settled", new FromSettled)

    /** The pledge has been granted. */
    val Granted = Value("granted", new FromGranted)

    /** The pledge has been revoked. */
    val Revoked = Value("revoked", new FromRevoked)

    /** The pledge has been cashed-in. */
    val CashedIn = Value("cashedIn", new FromCashedIn)

    /** The pledge has been rewarded. */
    val Rewarded = Value("rewarded", new FromRewarded)

    /** The pledge is awaiting the refund threshold. */
    val AwaitingRefundThreshold = Value("awaitingRefundThreshold", new FromAwaitingRefundThreshold)

    /** The pledge is awaiting coins for refund. */
    val AwaitingRefund = Value("awaitingRefund", new FromAwaitingRefund)

    /** The pledge has been refunded. */
    val Refunded = Value("refunded", new FromRefunded)

    /** The pledge has been neither cashed-in nor refunded. */
    val Unclaimed = Value("unclaimed", new FromUnclaimed)

    /**
      * Provides functionality for converting pledge states.
      */
    trait Transduction extends FsmTransduction {

      import play.api.Logger
      import play.api.Play.current
      import play.api.Play.configuration
      import play.api.libs.functional.syntax._
      import utils.common.typeExtensions._
      import utils.core.EmailHelper
      import services.auth.TechUsersRegistry._
      import services.auth.{UserDaoServiceComponent, AccountDaoServiceComponent}
      import services.auth.mongo.{MongoUserDaoComponent, MongoAccountDaoComponent}
      import services.pay.OrderDaoServiceComponent
      import services.pay.mongo.MongoOrderDaoComponent
      import services.pay.PayGateway
      import models.common.State._
      import mongo._

      @inline private val DefaultPerPage = 500

      private val RefundPeriod = configuration.getInt("core.project.refundPeriod").getOrElse(7200)
      private val RefundThreshold = configuration.getDouble("core.project.refundThreshold").getOrElse(10.0)
      private val RemindInterval = configuration.getInt("core.project.remindInterval").getOrElse(1440)

      private val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
        with MongoUserDaoComponent {
      }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

      private val orderService: OrderDaoServiceComponent#OrderDaoService = new OrderDaoServiceComponent
        with MongoOrderDaoComponent {
      }.daoService.asInstanceOf[OrderDaoServiceComponent#OrderDaoService]

      private val projectService: ProjectDaoServiceComponent#ProjectDaoService = new ProjectDaoServiceComponent
        with MongoProjectDaoComponent {
          def wip = false
      }.daoService.asInstanceOf[ProjectDaoServiceComponent#ProjectDaoService]

      private val pledgeService: PledgeDaoServiceComponent#PledgeDaoService = new PledgeDaoServiceComponent
        with MongoPledgeDaoComponent {
      }.daoService.asInstanceOf[PledgeDaoServiceComponent#PledgeDaoService]

      private val leftoverService: LeftoverDaoServiceComponent#LeftoverDaoService = new LeftoverDaoServiceComponent
        with MongoLeftoverDaoComponent {
      }.daoService.asInstanceOf[LeftoverDaoServiceComponent#LeftoverDaoService]

      /**
        * Sets the state of the pledges associated with the specified project.
        *
        * @param state      One of the [[PledgeFsm]] values.
        * @param projectId  The identifier of the project the pledges are associated with.
        * @return           A `Future` value containing the number of pledges affected.
        */
      private def setState(state: Ztate, projectId: String): Future[Int] = setState(
        state, Json.obj("projectId" -> projectId)
      )

      /**
        * Sets the state of the pledges that match the specified criteria.
        *
        * @param state    One of the [[PledgeFsm]] values.
        * @param selector The selector object.
        * @param check    A Boolean value indicating whether or not to check the current state.
        * @return         A `Future` value containing the number of pledges affected.
        */
      private def setState(state: Ztate, selector: JsObject, check: Boolean = true): Future[Int] = {
        var _selector = selector ++ {
          if (check) Json.obj("state.value" -> this.state)
          else Json.obj("$ne" -> Json.obj("state.value" -> state))
        }

        pledgeService.update(_selector, Json.obj("state" -> State(state)))
      }

      /**
        * Handles the `Save` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a tuple with the new
        *                 state of the pledge and the instance saved.
        */
      def apply(message: Save): Future[(Ztate, Pledge)] = {
        val pledge = Pledge().copy(message.pledge.asJson)
        pledge.state = Some(State(Settled))
        pledgeService.insert(pledge).map((Settled, _))
      }

      /**
        * Handles the `List` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the pledges of the project specified
        *                 in `message`, or an empty `Seq` if no pledges could be found.
        */
      def apply(message: List): Future[Seq[Pledge]] = pledgeService.find(
        Json.obj("projectId" -> message.projectId),
        Some(Json.obj("$exclude" -> Json.arr("state", "_version"))),
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        message.page, message.perPage
      )

      /**
        * Handles the `ListByState` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the pledges of the project specified in
        *                 `message` in a give state, or an empty `Seq` if no pledges could be found.
        */
      def apply(message: ListByState): Future[Seq[Pledge]] = pledgeService.find(
        Json.obj("projectId" -> message.projectId, "state.value" -> state),
        Some(Json.obj("$exclude" -> Json.arr("state", "_version"))),
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        message.page, message.perPage
      )

      /**
        * Handles the `Grant` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the affected pledges.
        */
      def apply(message: Grant): Future[Ztate] = setState(Granted, message.projectId).flatMap {
        case n if n > 0 => Future.successful(Granted)
        case _ => operationNotAllowed(message)
      }
    
      /**
        * Handles the `Revoke` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the affected pledges.
        */
      def apply(message: Revoke): Future[Ztate] = setState(Revoked, message.projectId).flatMap {
        case n if n > 0 => Future.successful(Revoked)
        case _ => operationNotAllowed(message)
      }

      /**
        * Handles the `CashIn` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the affected pledges.
        */
      def apply(message: CashIn): Future[Ztate] = setState(CashedIn, message.projectId).flatMap {
        case n if n > 0 => Future.successful(CashedIn)
        case _ => operationNotAllowed(message)
      }

      /**
        * Handles the `Reward` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the pledge.
        */
      def apply(message: Reward): Future[Ztate] = setState(Rewarded, Json.obj("id" -> message.pledgeId)).flatMap {
        case n if n > 0 => Future.successful(Rewarded)
        case _ => operationNotAllowed(message)
      }

      /**
        * Handles the `Refund` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the pledge.
        */
      def apply(message: Refund): Future[Ztate] = {
        val updateWrites = (
          (__ \ "state").write[State] ~
          (__ \ "backerInfo.refundCoinAddress").write[String]
        ).tupled

        // a backer might have pledged more than once
        pledgeService.update(
          Json.obj(
            "projectId" -> message.projectId,
            "backerInfo.accountId" -> message.accountId,
            "state.value" -> state
          ),
          updateWrites.writes(State(AwaitingRefundThreshold), message.coinAddress)
        ).flatMap { 
          case n if n > 0 => Future.successful(AwaitingRefundThreshold)
          case _ => operationNotAllowed(message)
        }
      }

      /**
        * Handles the `SetShippingInfo` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges affected.
        */
      def apply(message: SetShippingInfo): Future[Int] = {
        val updateWrites = (
          (__ \ "backerInfo.shippingAddress").write[Address] ~
          (__ \ "backerInfo.notice").writeNullable[String]
        ).tupled

        pledgeService.update(
          Json.obj("projectId" -> message.projectId),
          updateWrites.writes(message.shippingInfo.address, message.shippingInfo.notice)
        )
      }

      /**
        * Handles the `ReturnCoins` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges refunded.
        */
      def apply(message: ReturnCoins): Future[Int] = {
        def fixRates(transactionAmount: Double, selector: JsValue): Future[Map[String, (Double, Double)]] = {
          val coinsByCurrency = for {
            totals <- pledgeService.totals(selector)
            coins <- Future.sequence(
              totals.map { case (currency, total) =>
                PayGateway.rates(currency).map { rates =>
                  val cryptocurrency = PayGateway.Cryptocurrency
                  (currency -> (total * rates(cryptocurrency), rates(cryptocurrency)))
                }
              } 
            )
          } yield coins.toMap // map[currency -> (total, rate)]

          coinsByCurrency.map {
            case coins if coins.nonEmpty =>
              // sum all the totals in cryptocurrency
              val total = coins.map { case (k, v) => v._1 }.sum

              // calculate possible spread to fix rate
              val spread = ((transactionAmount * 100.0 / total) - 100.0) / 100.0

              // return a map[currency -> (fixedRate, currentRate)]
              coins.map {
                case (k, v) => (k, (((v._1 * (1.0 + spread)) * v._2) / v._1, v._2))
              }
            case _ => Map[String, (Double, Double)]()
          }
        }

        def returnCoins(refId: RefId): Future[Int] = {
          val selector = Json.obj("$in" -> Json.obj("id" -> Json.toJson(refId.mvalue)))
          val transactionFees = PayGateway.transactionFee * refId.mvalue.length

          fixRates(message.transaction.amount.value - transactionFees, selector).flatMap {
            case rates if rates.nonEmpty => setState(Refunded, selector).flatMap {
              case n if n > 0 => for {
                // get the pledges to refund
                pledges <- pledgeService.find(
                  selector, 
                  Some(Json.obj("$include" -> Json.arr("projectId", "amount", "backerInfo"))),
                  None, 0, refId.mvalue.length
                )

                orders <- Future.sequence(pledges.map { pledge =>
                  val pledgeAmount = pledge.amount.get.value
                  val fixedRate = rates(pledge.amount.get.currency)._1
                  val currentRate = rates(pledge.amount.get.currency)._2
                  val rate = math.min(fixedRate, currentRate)

                  // log possible leftover
                  (((pledgeAmount * fixedRate) ~) - ((pledgeAmount * currentRate) ~)) match {
                    case leftover => if (leftover > 0.0) leftoverService.incAmount(
                      Coin(leftover, message.transaction.amount.currency)
                    )
                  }

                  val amount = Coin(
                    (pledgeAmount * rate) ~, // truncate to 8 decimals to avoid overdraft
                    message.transaction.amount.currency,
                    Some(pledge.amount.get.currency),
                    Some(1.0 / rate)
                  )

                  val f = for {
                    order <- PayGateway.sendCoinsLocal(
                      accountId("core"),
                      amount,
                      pledge.backerInfo.get.refundCoinAddress.get,
                      Some(RefId("pledges", "id", pledge.id.get)),
                      message.transaction.orderId
                    )
                    _ <- userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                      projectService.find(Json.obj("id" -> pledge.projectId),
                        Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history", "_version"))),
                        None, 0, 1
                      ).map { _.headOption.foreach { project =>
                        EmailHelper.backer.sendRefundConfirmationEmail(backer, project, amount)
                      }}
                    }}
                  } yield Some(order)

                  f.recoverWith {
                    // set the state of the pledge to AwaitingRefundThreshold to give it
                    // a chance to get processed next time the refund threshold is reached
                    case e => setState(AwaitingRefundThreshold, Json.obj("id" -> pledge.id), false).map { _ =>
                      Logger.warn(s"could not refund pledge ${pledge.id.get}", e)
                      None
                    }
                  }
                }).map(_.filter(_.isDefined))
              } yield orders.length
              case _ => operationNotAllowed(message)
            }
            case _ => Future.failed(EmptyList("coins to return"))
          }
        }

        orderService.find(message.transaction.orderId).flatMap {
          case Some(order) => order.refId match {
            case Some(refId) if refId.domain == "pledges" => returnCoins(refId)
            case _ => Future.failed(UnreferencedObject("refund order", message.transaction.orderId.getOrElse("?")))
          }
          case _ => Future.failed(NotFound("refund order", message.transaction.orderId.getOrElse("?")))
        }
      }

      /**
        * Handles the `TakeUpAwaitingRefundThreshold` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges processed.
        */
      def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = {
        // implicit time used by pledgeService.totals
        implicit val now = DateTime.now(DateTimeZone.UTC)

        def sumCoins: Future[Double] = for {
          totals <- pledgeService.totals(state)
          coins <- Future.sequence(
            totals.map { case (currency, total) =>
              PayGateway.rates(currency).map { rates =>
                total * rates(PayGateway.Cryptocurrency)
              }
            } 
          )
        } yield coins.sum

        val f = for {
          totalAmount <- sumCoins
          pledgeCount <- totalAmount match {
            case amount if amount == 0.0 || amount < RefundThreshold => Future.successful(0)
            case amount => for {
              pledgeIds <- pledgeService.distinct("id", Json.obj(
                "state.value" -> state, "$lt" -> Json.obj("state.timestamp" -> now.getMillis)
              ))
              _ <- setState(
                AwaitingRefund, Json.obj("$lt" -> Json.obj("state.timestamp" -> now.getMillis))
              ) if pledgeIds.length > 0
              _ <- PayGateway.buyCoins(
                accountId("core"),
                Coin(
                  // ~~ rounds half-up to 8 decimals to ensure enough funds
                  (amount + (PayGateway.transactionFee * pledgeIds.length)) ~~,
                  PayGateway.Cryptocurrency,
                  Some(PayGateway.SupportedCurrencies(PayGateway.USD))
                  // exchange rate taken automatically
                ),
                Some(RefId("pledges", "id", pledgeIds))
              )
            } yield pledgeIds.length
          }
        } yield pledgeCount

        f.recoverWith {
          // rollback pledge state and forward error
          case e => setState(
            this.state, Json.obj("state.value" -> AwaitingRefund), false
          ).map { throw e }
        }
      }

      /**
        * Handles the `PollRevoked` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges revoked.
        * @note           Message `PollRevoked` does not alter the state of the pledges.
        */
      def apply(message: PollRevoked): Future[Int] = {
        val now = DateTime.now(DateTimeZone.UTC).getMillis
        var lastProject: Option[Project] = None

        def notifyBackers(page: Int, count: Int): Future[Int] = {
          for {
            // get revoked pledges in chunk of `DefaultPerPage`
            pledges <- pledgeService.find(
              Json.obj("state.value" -> state, "$lt" -> Json.obj("state.timestamp" -> now)),
              None, Some(Json.obj("$asc" -> Json.arr("projectId"))),
              page, DefaultPerPage
            )
            revokedCount <- pledges.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                _ <- Future.sequence(pledges.map { pledge =>
                  userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                    for {
                      currentProject <- lastProject match {
                        case Some(project) if project.id == pledge.projectId => Future.successful(lastProject)
                        case _ => /* project changed */ projectService.find(
                          Json.obj("id" -> pledge.projectId),
                          Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history", "_version"))),
                          None, 0, 1
                        ).map(_.headOption)
                      }
                    } yield currentProject.foreach { project =>
                      lastProject = currentProject
                      EmailHelper.backer.sendRefundReminderEmail(backer, project, RefundPeriod)
                    }
                  }}
                })

                // invoke notifyBackers recursively until there are no pledges left
                revokedCount <- pledges.length match {
                  case l if l == DefaultPerPage => notifyBackers(page + 1, count + l)
                  case _ => Future.successful(count)
                }
              } yield revokedCount
            }
          } yield revokedCount
        }

        notifyBackers(0, 0)
      }

      /**
        * Handles the `PullUnclaimed` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of unclaimed pledges pulled.
        */
      def apply(message: PullUnclaimed): Future[Int] = {
        // postpone this reminder by `ReminderInterval` since a refund reminder
        // has been sent just now
        val now = DateTime.now.getMillis
        var lastProject: Option[Project] = None

        def notifyBackers(page: Int, count: Int): Future[Int] = {
          for {
            // get unclaimed pledges in chunk of `DefaultPerPage`
            pledges <- pledgeService.find(
              Json.obj("state.value" -> Unclaimed, "$gte" -> Json.obj("state.timestamp" -> now)),
              None, Some(Json.obj("$asc" -> Json.arr("projectId"))),
              page, DefaultPerPage
            )
            unclaimedCount <- pledges.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                _ <- Future.sequence(pledges.map { pledge =>
                  userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                    for {
                      currentProject <- lastProject match {
                        case Some(project) if project.id == pledge.projectId => Future.successful(lastProject)
                        case _ => /* project changed */ projectService.find(
                          Json.obj("id" -> pledge.projectId),
                          Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history", "_version"))),
                          None, 0, 1
                        ).map(_.headOption)
                      }
                    } yield currentProject.foreach { project =>
                      lastProject = currentProject
                      EmailHelper.backer.sendRefundPeriodExpiredNotificationEmail(backer, project)
                    }
                  }}
                })

                // invoke notifyBackers recursively until there are no pledges left
                unclaimedCount <- pledges.length match {
                  case l if l == DefaultPerPage => notifyBackers(page + 1, count + l)
                  case _ => Future.successful(count)
                }
              } yield unclaimedCount
            }
          } yield unclaimedCount
        }

        setState(
          Unclaimed,
          Json.obj("$lt" -> Json.obj("state.timestamp" -> (now - (RefundPeriod * 60000))))
        ).flatMap {
          case n if n > 0 => notifyBackers(0, 0)
          case _ => Future.successful(0)
        }
      }
    }

    /**
      * Disables any conversion that is not allowed from state `New`.
      */
    class FromNew extends Transduction {

      def state = New

      override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Settled`.
      */
    class FromSettled extends Transduction {

      def state = Settled

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Granted`.
      */
    class FromGranted extends Transduction {

      def state = Granted

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Revoked`.
      */
    class FromRevoked extends Transduction {

      def state = Revoked

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      // override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      // override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `CashedIn`.
      */
    class FromCashedIn extends Transduction {

      def state = CashedIn

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Rewarded`.
      */
    class FromRewarded extends Transduction {

      def state = Rewarded

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `AwaitingRefundThreshold`.
      */
    class FromAwaitingRefundThreshold extends Transduction {

      def state = AwaitingRefundThreshold

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      // override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `AwaitingRefundThreshold`.
      */
    class FromAwaitingRefund extends Transduction {

      def state = AwaitingRefund

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Refunded`.
      */
    class FromRefunded extends Transduction {

      def state = Refunded

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Unclaimed`.
      */
    class FromUnclaimed extends Transduction {

      def state = Unclaimed

      override def apply(message: Save): Future[(Ztate, Pledge)] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Pledge]] = operationNotAllowed(message)
      // override def apply(message: ListByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: Grant): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Revoke): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: CashIn): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Refund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingRefundThreshold): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollRevoked): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullUnclaimed): Future[Int] = operationNotAllowed(message)
    }
  }
}
