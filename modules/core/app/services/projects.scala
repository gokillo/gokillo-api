/*#
  * @file projects.scala
  * @begin 15-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent._
import scala.language.implicitConversions
import scala.util.control.NonFatal
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.json.extensions._
import services.common._
import services.common.CommonErrors._
import services.common.DaoErrors._
import services.common.FsmErrors._
import services.pay.PayErrors._
import services.auth.AuthErrors._
import services.auth.SecureFsm
import models.common.{HistoryEvent, MetaFile, RefId, State}
import models.auth.{Token, User}
import models.auth.Role._
import models.auth.IdentityMode._
import models.core._
import models.pay._
import pledges.PledgeFsm
import ProjectUniverse._

package object projects {

  /** Implicit FSM used by JSON state serializer/deserializer. */
  implicit val fsm = ProjectFsm

  /**
    * Implements a finite state machine (FSM) for transducing project states.
    */
  object ProjectFsm extends SecureFsm {

    trait Message extends FsmMessage { def projectId: String }

    // FSM messages
    case class Save(project: Project) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class Update(projectId: String, update: Project) extends Message
    case class ChangeCoinAddress(projectId: String, currentCoinAddress: String, newCoinAddress: String) extends Message
    case class Submit(projectId: String) extends Message
    case class AcquireForAudit(projectId: String) extends Message
    case class Publish(projectId: String) extends Message
    case class Reject(projectId: String, comment: String) extends Message
    case class Edit(projectId: String) extends Message
    case class Relist(projectId: String, fundingInfo: FundingInfo) extends Message
    case class RefundPledges(projectId: String, coinAddress: String) extends Message
    case class SetShippingInfo(projectId: String, shippingInfo: ShippingInfo) extends Message
    case class GrantFunding(projectId: String, comment: String) extends Message
    case class Fund(projectId: String, coinAddress: String, feeRate: Option[Double] = None) extends Message
    case class RewardPledge(projectId: String, pledgeId: String) extends Message
    case class Close(projectId: String, comment: String) extends Message
    case class Delete(projectId: String) extends Message
    case class Pick(projectId: String) extends Message
    case class Unpick(projectId: String) extends Message
    case class Find(projectId: String) extends Message
    case class List(selector: Option[JsValue], projection: Option[JsValue],
      sort: Option[JsValue], page: Int, perPage: Int
    ) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class ListRandom(resNum: Int) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class Count() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class GetHistory(projectId: String) extends Message
    case class AddMedia(projectId: String, media: MetaFile, cover: Option[Boolean] = None) extends Message
    case class DeleteMedia(projectId: String, mediaId: String) extends Message
    case class FindMedia(projectId: String, mediaId: String) extends Message
    case class ListMedia(projectId: String) extends Message
    case class CreateReward(projectId: String, reward: Reward) extends Message
    case class UpdateReward(projectId: String, index: Int, update: Reward) extends Message
    case class DeleteReward(projectId: String, index: Int) extends Message
    case class FindReward(projectId: String, index: Int) extends Message
    case class FindRewardById(projectId: String, rewardId: String) extends Message
    case class ListRewards(projectId: String) extends Message
    case class AddRewardMedia(projectId: String, index: Int, media: MetaFile) extends Message
    case class DeleteRewardMedia(projectId: String, index: Int) extends Message
    case class FindRewardMedia(projectId: String, index: Int) extends Message
    case class CreateFaq(projectId: String, faq: Faq) extends Message
    case class UpdateFaq(projectId: String, index: Int, update: Faq) extends Message
    case class DeleteFaq(projectId: String, index: Int) extends Message
    case class FindFaq(projectId: String, index: Int) extends Message
    case class ListFaqs(projectId: String) extends Message
    case class IssuePaymentRequest(projectId: String, paymentRequest: PaymentRequest) extends Message
    case class ListBackers(projectId: String, page: Int, perPage: Int) extends Message
    case class CountBackers(projectId: String) extends Message
    case class ListPledges(projectId: String, page: Int, perPage: Int) extends Message
    case class ListPledgesByState(projectId: String, pledgeState: String, page: Int, perPage: Int) extends Message
    case class AddPledge(transaction: Transaction) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class SellCoins(transaction: Transaction) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class SendCoins(transaction: Transaction) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class ReturnCoins(transaction: Transaction) extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class UnlistExpired() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class TakeUpAwaitingOrdersCompletion() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class PollSucceeded() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }
    case class PullMissed() extends Message {
      def projectId: String = throw new UnsupportedOperationException
    }

    private[ProjectFsm] class Val(name: String, convertWith: FsmTransduction) extends super.Val(name, convertWith) {

      def !(message: Save) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Update) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ChangeCoinAddress) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Submit) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AcquireForAudit) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Publish) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Reject) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Edit) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Relist) = convertWith.asInstanceOf[Transduction](message)
      def !(message: RefundPledges) = convertWith.asInstanceOf[Transduction](message)
      def !(message: SetShippingInfo) = convertWith.asInstanceOf[Transduction](message)
      def !(message: GrantFunding) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Fund) = convertWith.asInstanceOf[Transduction](message)
      def !(message: RewardPledge) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Close) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Delete) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Pick) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Unpick) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Find) = convertWith.asInstanceOf[Transduction](message)
      def !(message: List) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListRandom) = convertWith.asInstanceOf[Transduction](message)
      def !(message: Count) = convertWith.asInstanceOf[Transduction](message)
      def !(message: GetHistory) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AddMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: DeleteMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: CreateReward) = convertWith.asInstanceOf[Transduction](message)
      def !(message: UpdateReward) = convertWith.asInstanceOf[Transduction](message)
      def !(message: DeleteReward) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindReward) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindRewardById) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListRewards) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AddRewardMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: DeleteRewardMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindRewardMedia) = convertWith.asInstanceOf[Transduction](message)
      def !(message: CreateFaq) = convertWith.asInstanceOf[Transduction](message)
      def !(message: UpdateFaq) = convertWith.asInstanceOf[Transduction](message)
      def !(message: DeleteFaq) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindFaq) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListFaqs) = convertWith.asInstanceOf[Transduction](message)
      def !(message: IssuePaymentRequest) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListBackers) = convertWith.asInstanceOf[Transduction](message)
      def !(message: CountBackers) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListPledges) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListPledgesByState) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AddPledge) = convertWith.asInstanceOf[Transduction](message)
      def !(message: SellCoins) = convertWith.asInstanceOf[Transduction](message)
      def !(message: SendCoins) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ReturnCoins) = convertWith.asInstanceOf[Transduction](message)
      def !(message: UnlistExpired) = convertWith.asInstanceOf[Transduction](message)
      def !(message: TakeUpAwaitingOrdersCompletion) = convertWith.asInstanceOf[Transduction](message)
      def !(message: PollSucceeded) = convertWith.asInstanceOf[Transduction](message)
      def !(message: PullMissed) = convertWith.asInstanceOf[Transduction](message)
    }

    protected def Val(name: String, convertWith: FsmTransduction) = new Val(name, convertWith)

    /**
      * Implicitly converts the specified state value to a `Val`.
      *
      * @param value  The state value to convert.
      * @return       The `Val` converted from `value`.
      */
    implicit def toVal(value: Value) = value.asInstanceOf[Val]

    /** The project has just been created. */
    val New = Value("new", new FromNew)

    /** The project is being edited. */
    val Open = Value("open", new FromOpen)

    /** The project has been submitted for audit. */
    val Submitted = Value("submitted", new FromSubmitted)

    /** The project is being audited. */
    val Audit = Value("audit", new FromAudit)

    /** The project has been published. */
    val Published = Value("published", new FromPublished)

    /** The project is awaiting completion of pending orders. */
    val AwaitingOrdersCompletion = Value("awaitingOrdersCompletion", new FromAwaitingOrdersCompletion)

    /** The project is awaiting coins for funding. */
    val AwaitingCoins = Value("awaitingCoins", new FromAwaitingCoins)

    /** The project has been rejected. */
    val Rejected = Value("rejected", new FromRejected)

    /** The project has reached its funding target. */
    val Succeeded = Value("succeeded", new FromSucceeded)

    /** The project has been closed and successfully funded. */
    val Funded = Value("funded", new FromFunded)

    /** The project has been closed without being funded. */
    val Closed = Value("closed", new FromClosed)

    /** The project has been deleted. */
    val Deleted = Value("deleted", new FromDeleted)

    /**
      * Provides functionality for converting project states.
      */
    trait Transduction extends SecureFsmTransduction {

      import scala.collection.mutable.{ArrayBuilder, Map => MutableMap}
      import scala.util.{Random, Success}
      import play.api.libs.functional.syntax._
      import play.api.libs.iteratee._
      import play.api.Play.current
      import play.api.Play.configuration
      import utils.common._
      import utils.common.typeExtensions._
      import utils.core.EmailHelper
      import services.auth.TechUsersRegistry._
      import services.auth.{UserDaoServiceComponent, AccountDaoServiceComponent}
      import services.auth.mongo._
      import services.common.{FsServiceComponent, DefaultFsServiceComponent}
      import services.core.mongo.MongoAlgorithmFsComponent
      import services.pay.OrderDaoServiceComponent
      import services.pay.mongo.MongoOrderDaoComponent
      import services.pay.PayGateway
      import models.common.State._
      import models.pay.OrderStatus._
      import models.pay.RateType._
      import mongo._
      import machineLearning._

      @inline private val DefaultPerPage = 500

      private val FeeRate = configuration.getDouble("core.project.feeRate").getOrElse(5.0)
      private val VatRate = configuration.getDouble("core.project.vatRate").getOrElse(0.0)
      private val CashInPeriod = configuration.getInt("core.project.cashInPeriod").getOrElse(7200)
      private val RefundPeriod = configuration.getInt("core.project.refundPeriod").getOrElse(7200)

      private val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
        with MongoUserDaoComponent {
      }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

      private val pledgeService: PledgeDaoServiceComponent#PledgeDaoService = new PledgeDaoServiceComponent
        with MongoPledgeDaoComponent {
      }.daoService.asInstanceOf[PledgeDaoServiceComponent#PledgeDaoService]

      private val feeService: FeeDaoServiceComponent#FeeDaoService = new FeeDaoServiceComponent
        with MongoFeeDaoComponent {
      }.daoService.asInstanceOf[FeeDaoServiceComponent#FeeDaoService]

      private val orderService: OrderDaoServiceComponent#OrderDaoService = new OrderDaoServiceComponent
        with MongoOrderDaoComponent {
      }.daoService.asInstanceOf[OrderDaoServiceComponent#OrderDaoService]

      private val algorithmService: FsServiceComponent#FsService = new DefaultFsServiceComponent
        with MongoAlgorithmFsComponent {
      }.fsService

      protected val authorizeImpl = MutableMap[String, (String, Token) => Boolean](
        "IssuePaymentRequest" -> ((accountId: String, token: Token) => token.roles.contains(Member.id)),
        "RefundPledges" -> ((accountId: String, token: Token) => token.roles.contains(Member.id)),
        "SetShippingInfo" -> ((accountId: String, token: Token) => token.roles.contains(Member.id)),
        "Find" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "List" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id)),
        "ListRandom" -> ((accountId: String, token: Token) => false),
        "Count" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id)),
        "FindMedia" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListMedia" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "FindReward" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "FindRewardById" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListRewards" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "FindRewardMedia" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "FindFaq" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListFaqs" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListBackers" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "CountBackers" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListPledges" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "ListPledgesByState" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId),
        "GetHistory" -> ((accountId: String, token: Token) => token.roles.contains(Auditor.id) || token.account == accountId)
      ).withDefaultValue(
        (accountId: String, token: Token) => token.account == accountId
      )

      /**
        * Gets the context this `Transduction` operates in.
        * @return The DAO service used by this `Transduction`.
        */
      def context: ProjectDaoServiceComponent#ProjectDaoService

      /**
        * Executes this `Transduction` by applying the specified function to the
        * specified message.
        *
        * @tparam T       The type of the value returned by `f`.
        * @param message  The message being processed.
        * @param f        The function that actually performs the transduction.
        * @return         A `Future` value containing an instance of `T`.
        */
      protected def exec[T](message: Message, f: Project => Future[T]): Future[T] = {
        val selectorWrites = (
          (__ \ "id").write[String] ~
          (__ \ "state.value").writeNullable[String]
        ).tupled

        context.find(
          selectorWrites.writes(message.projectId, Some(state)),
          None, None, 0, 1
        ).flatMap { _.headOption match {
          case Some(project) =>
            // get authorized if security is enabled; otherwise pass thru
            authorize(message, project.accountId.getOrElse(""), () => project.state match {
              case Some(projectState) if (projectState.value == this.state.toString) => f(project)
              case _ =>
                val projectState = project.state match {
                  case Some(state) => state.value
                  case _ => "undefined"
                }
                Future.failed(InvalidState("project", message.projectId, projectState))
              }
            )
          case _ => Future.failed(NotFoundInState("project", message.projectId, state))
        }}
      }

      /**
        * Sets the state of the project identified by the specified id to the
        * specified value.
        *
        * @param message      The message being processed.
        * @param state        One of the [[ProjectFsm]] values.
        * @param historyEvent An `Option` value containing the event description,
        *                     or `None` if unspecified.
        * @return             A `Future` value containing the new state of the project.
        */
      private def setState(
        message: Message,
        state: Ztate,
        historyEvent: Option[HistoryEvent] = None
      ): Future[Ztate] = exec(message, project => setState(project, state, historyEvent))

      private def setState(
        project: Project,
        state: Ztate,
        historyEvent: Option[HistoryEvent]
      ): Future[Ztate] = {
        val history = historyEvent.map { event =>
          Json.obj("history" -> (project.asJson.get(__ \ 'history) match {
            case _: JsUndefined => Seq[JsValue](event.asJson)
            case js: JsValue => js.as[JsArray].value :+ event.asJson
          }))
        } getOrElse Json.obj()

        context.update(
          Json.obj("id" -> project.id, "_version" -> project.version),
          Json.obj("state" -> State(state)) ++ history
        ).map {
          case n if n > 0 => state
          case _ => throw StaleObject(project.id.get, context.collectionName)
        }
      }

      /**
        * Handles the `Save` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a tuple with the new
        *                 state of the project and the instance saved.
        */
      def apply(message: Save): Future[(Ztate, Project)] = {
        val project = Project().copy(message.project)

        checkAmount(Coin(
          project.fundingInfo.get.targetAmount.getOrElse(0.0),
          project.fundingInfo.get.currency.getOrElse("")
        )).flatMap { _ =>
          implicit val precision = project.fundingInfo.get.currency match {
            case Some(currency) if currency != PayGateway.Cryptocurrency => Precision(0.01)
            case _ => Precision(0.00000001)
          }

          project.state = Some(State(Open))
          project.fundingInfo = project.fundingInfo.map { fi =>
            fi.targetAmount = fi.targetAmount.map(_ ~~); fi
          }
          project.rewards = project.rewards.map { _.map { reward =>
            reward.id = Some(context.generateId)
            reward.pledgeAmount = reward.pledgeAmount.map(_ ~~)
            reward
          }}
          context.insert(project).map((Open, _))
        }
      }

      /**
        * Handles the `Update` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Update): Future[Ztate] = {
        def update(project: Project, update: Project): Future[Ztate] = {
          update.state = Some(State(Open))
          update.fundingInfo.map { fi =>
            // prevent target coin address from being overwritten
            fi.coinAddress = None

            // currency can no longer be changed once the project has been published
            fi.currency match {
              case None =>
              case currency => project.fundingInfo.foreach { fundingInfo =>
                if (currency != fundingInfo.currency) {
                  daoService.find(
                    Json.obj("id" -> project.id, "state.value" -> Published),
                    None, None, 0, 1
                  ).map {
                    case seq if seq.isEmpty =>
                    case _ => throw TransductionNotPossible(
                      "project", project.id.get, Open,
                      "currency can no longer be changed since project has been already published"
                    )
                  }
                }
              }
            }

            // if funding duration changes, then recalculate funding end time
            fi.duration.foreach { _ => project.fundingInfo match {
              case Some(fundingInfo) => fi.startTime = fundingInfo.startTime
              case _ => fi.startTime = Some(DateTime.now(DateTimeZone.UTC))
            }}

            val _fi = project.fundingInfo.map(_.copy(fi)) getOrElse fi
            update.fundingInfo = Some(_fi)
          }

          context.update(
            Json.obj("id" -> project.id, "_version" -> project.version),
            update.asJson
          ).map {
            case n if n > 0 => Open
            case _ => throw StaleObject(project.id.get, context.collectionName)
          }
        }

        exec(message, project => {{
          message.update.fundingInfo match {
            case Some(fundingInfo) if fundingInfo.targetAmount.isDefined || fundingInfo.currency.isDefined =>
              val targetAmount = fundingInfo.targetAmount match {
                case Some(targetAmount) => targetAmount
                case None => project.fundingInfo.get.targetAmount.get
              }
              val currency = fundingInfo.currency match {
                case Some(currency) => currency
                case None => project.fundingInfo.get.currency.get
              }
              checkAmount(Coin(targetAmount, currency))
            case _ => Future.successful(Unit)
          }}.flatMap { _ =>
            implicit val precision = project.fundingInfo.get.currency match {
              case Some(currency) if currency != PayGateway.Cryptocurrency => Precision(0.01)
              case _ => Precision(0.00000001)
            }

            val _update = message.update
            _update.fundingInfo = _update.fundingInfo.map { fi =>
              fi.targetAmount = fi.targetAmount.map(_ ~~); fi
            }
            _update.rewards = _update.rewards.map { _.map { reward =>
              if (!reward.id.isDefined) reward.id = Some(context.generateId)
              reward.pledgeAmount = reward.pledgeAmount.map(_ ~~)
              reward
            }}

            update(project, Project().copy(_update))
          }
        })
      }

      /**
        * Handles the `ChangeCoinAddress` message.
        *
        * @param message  The message to handle.
        * @note           Message `ChangeCoinAddress` does not alter the state of the project.
        */
      def apply(message: ChangeCoinAddress): Future[Unit] = exec(
        message, project => {
          project.fundingInfo match {
            case Some(fundingInfo) => fundingInfo.coinAddress match {
              case Some(coinAddress) if coinAddress == message.currentCoinAddress =>
                context.update(
                  Json.obj("id" -> message.projectId, "_version" -> project.version),
                  Json.obj("fundingInfo.coinAddress" -> message.newCoinAddress)
                ).map {
                  case n if n > 0 =>
                  case _ => throw StaleObject(project.id.get, context.collectionName)
                }
              case _ => val username = this.token.asInstanceOf[Option[Token]] match {
                  case Some(token) => token.username
                  case _ => "anonym"
                }
                throw NotAuthorized("operation", message.name, username, "current coin address does not match")
            }
            case _ => throw NotAllowed("operation", message.name, "no funding info")
          }
        }
      )

      /**
        * Handles the `Submit` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Submit): Future[Ztate] = exec(
        message, project => {
          setState(
            project, Submitted, None
          ).flatMap { newState =>
            fsService.update(
              Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
              Json.obj("metadata.state" -> State(newState))
            ).map { _ => newState }
          }
        }
      )
      
      /**
        * Handles the `AcquireForAudit` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: AcquireForAudit): Future[Ztate] = exec(
        message, project => {
          setState(
            project, Audit, None
          ).flatMap { newState =>
            fsService.update(
              Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
              Json.obj("metadata.state" -> State(newState))
            ).map { _ => newState }
          }
        }
      )

      /**
        * Handles the `Publish` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Publish): Future[Ztate] = {
        def insert(project: Project): Future[Project] = {
          project.fundingInfo = project.fundingInfo.map { fi =>
            // start fundraising period now
            fi.startTime = Some(DateTime.now(DateTimeZone.UTC)); fi
          }

          // transduce media state to published
          fsService.update(
            Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
            Json.obj("metadata.state" -> State(Published))
          ).flatMap { _ =>
            daoService.insert(project)
          }
        }

        def update(current: Project, project: Project): Future[Project] = current.state match {
          case Some(currentState) if (currentState.value == Succeeded) => Future.failed(TransductionNotPossible(
              "project", message.projectId, Published,
              "project already reached its funding target"
            ))
          case Some(currentState) if (currentState.value == Funded) => Future.failed(TransductionNotPossible(
              "project", message.projectId, Published,
              "project already funded"
            ))
          case _ =>
            // transduce media state to published
            fsService.update(
              Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
              Json.obj("metadata.state" -> State(Published))
            ).flatMap { _ =>
              daoService.findAndUpdate(
                Json.obj("id" -> message.projectId, "_version" -> current.version),
                project.asJson.delete(__ \ 'id).delete(__ \ '_version)
              ).flatMap {
                // remove old media
                case Some(old) => fsService.remove(Json.obj(
                    "metadata.projectId" -> old.pseudoid,
                    "metadata.state.value" -> Published
                  )).map { _ => old }
                case _ => Future.failed(StaleObject(message.projectId, context.collectionName))
              }
            }
        }

        exec(
          message, project => {
            project.state = Some(State(Published))
            daoService.find(
              message.projectId,
              Some(Json.obj("$include" -> Json.arr("state", "_version")))
            ).flatMap {
              // publish project (update or insert)
              case Some(current) => update(current, project)
              case _ => insert(project)
            }.flatMap { _ =>
              // remove wip project
              context.remove(Json.obj("id" -> message.projectId, "_version" -> project.version)).flatMap { _ =>
                userService.findByAccountId(project.accountId, false).map { _.foreach { originator =>
                  EmailHelper.originator.sendPublishingConfirmationEmail(originator, project)
                }};
              }
            }.map { _ => Published }
          }
        )
      }

      /**
        * Handles the `Relist` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Relist): Future[Ztate] = exec(
        message, project => {
          val event = HistoryEvent("Relisted")
          val history = Json.obj("history" -> (project.asJson.get(__ \ 'history) match {
            case _: JsUndefined => Seq[JsValue](event.asJson)
            case js: JsValue => js.as[JsArray].value :+ event.asJson
          }))

          context.update(
            Json.obj("id" -> message.projectId, "_version" -> project.version),
            Json.obj("state" -> State(Published), "fundingInfo" -> message.fundingInfo) ++ history
          ).flatMap {
            case n if n > 0 =>
              fsService.update(
                Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
                Json.obj("metadata.state" -> State(Published))
              ).map { _ => Published }
            case _ => throw StaleObject(message.projectId, context.collectionName)
          }
        }
      )

      /**
        * Handles the `RefundPledges` message.
        *
        * @param message  The message to handle.
        * @note           Message `RefundPledges` does not alter the state of the project.
        */
      def apply(message: RefundPledges): Future[Unit] = message.coinAddress match {
        case coinAddress if coinAddress.isCoinAddress => exec(
          message, project => {
            this.token.asInstanceOf[Option[Token]] match {
              case Some(token) => (PledgeFsm(PledgeFsm.Revoked) ! PledgeFsm.Refund(
                message.projectId, token.account, message.coinAddress
              )).map { _ => }
              case _ => operationNotAllowed(message)
            }
          }
        )
        case coinAddress => Future.failed(InvalidCoinAddress(coinAddress))
      }

      /**
        * Handles the `SetShippingInfo` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges affected.
        * @note           Message `SetShippingInfo` does not alter the state of the project.
        */
      def apply(message: SetShippingInfo): Future[Int] = exec(
        message, project => {
          this.token.asInstanceOf[Option[Token]] match {
            case Some(token) => (PledgeFsm(PledgeFsm.Granted) ! PledgeFsm.SetShippingInfo(
              message.projectId, message.shippingInfo
            ))
            case _ => operationNotAllowed(message)
          }
        }
      )
      
      /**
        * Handles the `Reject` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Reject): Future[Ztate] = exec(
        message, project => {
          setState(
            project, Rejected, Some(HistoryEvent("Rejected", Some(message.comment)))
          ).flatMap { newState =>
            fsService.update(
              Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
              Json.obj("metadata.state" -> State(newState))
            ).map { _ => context.find(message.projectId).map { _.foreach { project =>
              userService.findByAccountId(project.accountId, false).map { _.foreach { originator =>
                EmailHelper.originator.sendProjectRejectedNotificationEmail(originator, project, message.comment)
              }}}}
              newState
            }
          }
        }
      )

      /**
        * Handles the `Edit` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Edit): Future[Ztate] = exec(
        message, project => (context eq wipService) match {
          case true => // project already work in progress: update state
            setState(
              project, Open, None
            ).flatMap { newState =>
              fsService.update(
                Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
                Json.obj("metadata.state" -> State(newState))
              ).map { _ => newState }
            }
          case false => // project not work in progress: create new copy
            val wip = Project(pseudoid = Some(wipService.generateId), state = Some(State(Open)))
            wipService.insert(project.copy(wip)).map { _ =>
              fsService.find(Json.obj(
                "metadata.projectId" -> project.pseudoid,
                "metadata.state.value" -> state
              )).foreach { files => files.foreach { file =>
                fsService.save(file.filename, file.contentType, fsService.enumerate(file)).foreach { newFile =>
                  fsService.update(
                    newFile.id,
                    Json.obj("metadata" -> file.metadata)
                      .set(__ \ 'metadata \ 'state \ 'value -> ztateFormat.writes(Open))
                      .set(__ \ 'metadata \ 'projectId -> JsString(wip.pseudoid.get))
                  ) 
                }
              }}; Open
            }
        }
      )

      /**
        * Handles the `GrantFunding` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: GrantFunding): Future[Ztate] = exec(
        message, project => grantFunding(project, Some(message.comment), true)
      )

      /**
        * Handles the `Fund` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Fund): Future[Ztate] = {
        exec(message, project => project.fundingInfo match {
          case Some(fundingInfo) if fundingInfo.coinAddress == Some(message.coinAddress) =>
            setState(project, AwaitingCoins, None).flatMap { newState =>
              val fee = fundingInfo.raisedAmount.map(_ * message.feeRate.getOrElse(FeeRate) / 100.0) getOrElse 0.0
              val vat = (fee * VatRate) / 100.0
              fsService.update(
                Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
                Json.obj("metadata.state" -> State(newState))
              ).flatMap { _ =>
                implicit val precision = Precision(0.00000001)
                PayGateway.rates(fundingInfo.currency.get, Ask).flatMap { rates =>
                  val rate = Some(1.0 / rates(PayGateway.Cryptocurrency))
                  PayGateway.buyCoins(
                    accountId("core"),
                    Coin(
                      // ~ truncates to 8 decimals to avoid overspending
                      (fundingInfo.raisedAmount.map(_ - fee - vat).get * rates(PayGateway.Cryptocurrency)) ~,
                      PayGateway.Cryptocurrency,
                      fundingInfo.currency,
                      rate
                    ),
                    Some(RefId("projects", "id", project.id.get))
                  ).flatMap { _ => 
                    feeService.insert(Fee(
                      None,
                      project.id.get,
                      Coin(
                        fee * rates(PayGateway.Cryptocurrency),
                        PayGateway.Cryptocurrency,
                        fundingInfo.currency,
                        rate
                      ),
                      if (vat > 0.0) Some(Coin(
                        vat * rates(PayGateway.Cryptocurrency),
                        PayGateway.Cryptocurrency,
                        fundingInfo.currency,
                        rate
                      )) else None
                    ))
                  }.map { _ => newState }
                }
              }.recoverWith {
                // rollback project state and forward error
                case e => setState(project, this.state, None).map { throw e }
              }
            }
          case _ /* if fundingInfo.coinAddress != Some(message.coinAddress) */ =>
            Future.failed(TransductionNotPossible(
              "project", message.projectId, Published,
              "target coin address does not match"
            ))
        })
      }

      /**
        * Handles the `RewardPledge` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a the new state of the pledge.
        */
      def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = exec(
        message, project => {
          PledgeFsm(PledgeFsm.CashedIn) ! PledgeFsm.Reward(message.projectId, message.pledgeId)
        }
      )

      /**
        * Handles the `Close` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Close): Future[Ztate] = exec(
        message, project => close(project, Some(message.comment))
      )

      /**
        * Handles the `Delete` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        */
      def apply(message: Delete): Future[Ztate] = exec(
        message, project => {
          context.remove(Json.obj("id" -> message.projectId, "_version" -> project.version)).map {
            case n if n > 0 => fsService.remove(
              Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state)
            )
            case _ => throw StaleObject(message.projectId, context.collectionName)
          }.map { _ => Deleted }
        }
      )

      /**
        * Handles the `Pick` message.
        *
        * @param message  The message to handle.
        * @note           Message `Pick` does not alter the state of the project.
        */
      def apply(message: Pick): Future[Unit] = exec(
        message, project => {
          context.update(
            Json.obj("id" -> message.projectId, "_version" -> project.version),
            Json.obj("picked" -> true)
          ).map {
            case n if n > 0 =>
            case _ => throw StaleObject(message.projectId, context.collectionName)
          }
        }
      )

      /**
        * Handles the `Unpick` message.
        *
        * @param message  The message to handle.
        * @note           Message `Unpick` does not alter the state of the project.
        */
      def apply(message: Unpick): Future[Unit] = exec(
        message, project => {
          context.update(
            Json.obj("id" -> message.projectId, "_version" -> project.version),
            Json.obj("picked" -> JsNull)
          ).map {
            case n if n > 0 =>
            case _ => throw StaleObject(message.projectId, context.collectionName)
          }
        }
      )

      /**
        * Handles the `Find` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the project identified by the id
        *                 specified in `message`, or `None` if the project is not in
        *                 this state or could not be found.
        */
      def apply(message: Find): Future[Option[Project]] = exec(
        message, project => {
          project.pseudoid = None
          project.history = None
          project.version = None
          Future.successful(Some(project))
        }
      ).map { _.map { project =>
        project.fundingInfo.map { fundingInfo =>
          fundingInfo.coinAddress = this.token.asInstanceOf[Option[Token]] match {
            case Some(token) if token.roles.contains(Superuser.id) => fundingInfo.coinAddress
            case Some(token) =>
              // if the principal is the owner of the project,
              // then show the last four digits of the coin address
              if (project.accountId.map(_ == token.account) getOrElse false)
                fundingInfo.coinAddress.map(_.replaceAll(".(?=.{4})", "*"))
              else None
            case _ => None
          }
          project.fundingInfo = Some(fundingInfo)
        }
        project
      }}.recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `List` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a `Seq` of projects that match
        *                 the selection criteria specified in `message`, or an empty
        *                 `Seq` if no projects could be found in this state.
        */
      def apply(message: List): Future[Seq[Project]] = authorize(message, null, () => {
        val selector = Json.obj("state.value" -> state)
        var exclude = Json.arr("pseudoid", "rewards", "faqs", "history", "_version")

        this.token.asInstanceOf[Option[Token]] match {
          case Some(token) if token.roles.contains(Superuser.id) =>
          case _ => exclude = exclude :+ JsString("fundingInfo.coinAddress")
        }

        val projection = Json.obj("$exclude" -> exclude)

        context.find(
          message.selector.map(_.as[JsObject] ++ selector) getOrElse selector,
          message.projection.map(_.as[JsObject] ++ projection) orElse Some(projection),
          message.sort orElse Some(Json.obj("$asc" -> Json.arr("name"))),
          message.page, message.perPage
        )
      })

      /**
        * Handles the `ListRandom` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a `Seq` of projects selected randomly,
        *                 or an empty `Seq` if no projects could be found in this state.
        */
      def apply(message: ListRandom): Future[Seq[Project]] = authorize(message, null, () => {
        val selector = Json.obj("state.value" -> state)
        context.count(selector).flatMap {
          case n if n > 0 =>
            val perPage = message.resNum * 10
            val randomPage = Random.nextInt(math.max(1, n / perPage))
            context.find(
              selector,
              Some(Json.obj("$exclude" -> Json.arr("pseudoid", "fundingInfo.coinAddress", "rewards", "faqs", "history", "_version"))),
              None, randomPage, perPage
            ).map { projects =>
              Random.shuffle(projects) take message.resNum
            }
          case _ => Future.successful(Seq.empty[Project])
        }
      })

      /**
        * Handles the `Count` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of projects in this state.
        */
      def apply(message: Count): Future[Int] = authorize(message, null, () => {
        context.count(Json.obj("state.value" -> state))
      })

      /**
        * Handles the `GetHistory` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the history of the project
        *                 specified in `message`, or an empty `Seq` if the project
        *                 could not be found or no history could be found.
        */
      def apply(message: GetHistory): Future[Seq[HistoryEvent]] = exec(
        message, project => {
          Future.successful(project.history.getOrElse(Seq.empty))
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `AddMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        * @note           Message `AddMedia` does not alter the state of the project.
        */
      def apply(message: AddMedia): Future[Ztate] = exec(
        message, project => {
          var update = Json.obj(
            "projectId" -> project.pseudoid,
            "category" -> "project",
            "state" -> State(state)
          )

          message.cover match {
            case Some(cover) if cover => update = update ++ Json.obj("cover" -> cover)
            case _ =>
          }

          fsService.update(
            message.media.id, Json.obj("metadata" -> update)
          ).map { _ => state }
        }
      ).recoverWith { case e =>
        fsService.remove(Json.obj("id" -> message.media.id)).map { _ => throw e }
      }

      /**
        * Handles the `DeleteMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the project.
        * @note           Message `DeleteMedia` does not alter the state of the project.
        */
      def apply(message: DeleteMedia): Future[Ztate] = exec(
        message, project => {
          fsService.remove(Json.obj(
            "id" -> message.mediaId,
            "metadata.state.value" -> state
          )).map {
            case n if n > 0 => Deleted
            case _ => throw ElementNotFound("media", message.mediaId, "project", message.projectId)
          }
        }
      )

      /**
        * Handles the `FindMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the media file of the
        *                 project, or `None` if it could not be found.
        */
      def apply(message: FindMedia): Future[Option[MetaFile]] = exec(
        message, project => {
          fsService.find(Json.obj(
            "id" -> message.mediaId,
            "metadata.state.value" -> state
          )).map(_.headOption)
        }
      ).recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `ListMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the media files of the
        *                 project, or an empty `Seq` if no media could be found.
        */
      def apply(message: ListMedia): Future[Seq[MetaFile]] = exec(
        message, project => {
          fsService.find(Json.obj(
            "metadata.projectId" -> project.pseudoid,
            "metadata.category" -> "project",
            "metadata.state.value" -> state
          ))
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `CreateReward` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a tuple with the new
        *                 state of the project and the zero-based index at which the
        *                 reward was added.
        * @note           Message `CreateReward` does not alter the state of the project.
        */
      def apply(message: CreateReward): Future[(Ztate, Int)] = {
        wipService.addReward(message.projectId, message.reward)(state).map {
          case Some(index) => (state, index)
          case _ => throw StaleObject(message.projectId, context.collectionName)
        }
      }

      /**
        * Handles the `UpdateReward` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        * @note           Message `UpdateReward` does not alter the state of the project.
        */
      def apply(message: UpdateReward): Future[Ztate] = exec(
        message, project => {
          if (message.index < 0 || message.index >= project.rewards.getOrElse(Seq.empty).length) {
            Future.failed(ElementNotFound("reward", message.index.toString, "project", message.projectId))
          } else {
            message.update.id = None // reward id cannot be modified
            wipService.updateReward(message.projectId, message.index, message.update)(state).map {
              case Some(_) => state
              case _ => throw StaleObject(message.projectId, context.collectionName)
            }
          }
        }
      )

      /**
        * Handles the `DeleteReward` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the project.
        * @note           Message `DeleteReward` does not alter the state of the project.
        */
      def apply(message: DeleteReward): Future[Ztate] = exec(
        message, project => {
          if (message.index < 0 || message.index >= project.rewards.getOrElse(Seq.empty).length) {
            Future.failed(ElementNotFound("reward", message.index.toString, "project", message.projectId))
          } else context.removeReward(message.projectId, message.index)(state).flatMap {
            case Some(reward) => fsService.remove(Json.obj(
              "metadata.projectId" -> project.pseudoid,
              "metadata.rewardId" -> reward.id,
              "metadata.state.value" -> state
            )).map { _ => ProjectFsm(project.state.get) }
            case _ => Future.failed(StaleObject(message.projectId, context.collectionName))
          }
        }
      )

      /**
        * Handles the `FindReward` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the reward specified
        *                 in `message`, or `None` if it could not be found.
        */
      def apply(message: FindReward): Future[Option[Reward]] = exec(
        message, project => {
          Future.successful {
            val rewards = project.rewards.getOrElse(Seq.empty)
            if (message.index < 0 || message.index >= rewards.length) None
            else Some(rewards(message.index))
          }
        }
      ).recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `FindRewardById` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the reward specified
        *                 in `message`, or `None` if it could not be found.
        */
      def apply(message: FindRewardById): Future[Option[Reward]] = exec(
        message, project => {
          Future.successful {
            project.rewards.getOrElse(Seq.empty) find {
              case reward => reward.id.get == message.rewardId
            } orElse None
          }
        }
      ).recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `ListRewards` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the rewards of the project
        *                 specified in `message`, or an empty `Seq` if no rewards
        *                 could be found.
        */
      def apply(message: ListRewards): Future[Seq[Reward]] = exec(
        message, project => {
          Future.successful(project.rewards.getOrElse(Seq.empty))
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `AddRewardMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        * @note           Message `AddRewardMedia` does not alter the state of the project.
        */
      def apply(message: AddRewardMedia): Future[Ztate] = exec(
        message, project => {
          val rewards = project.rewards.getOrElse(Seq.empty)
          if (message.index < 0 || message.index >= rewards.length) {
            Future.failed(ElementNotFound("reward", message.index.toString, "project", message.projectId))
          } else {
            val reward = rewards(message.index)
            fsService.update(message.media.id, Json.obj("metadata" -> Json.obj(
              "projectId" -> project.pseudoid,
              "rewardId" -> reward.id,
              "category" -> "reward",
              "state" -> State(state)
            ))).map { _ =>
              fsService.remove(Json.obj(
                "$lt" -> Json.obj("uploadDate" -> message.media.uploadDateMillis),
                "metadata.projectId" -> project.pseudoid,
                "metadata.rewardId" -> reward.id,
                "metadata.state.value" -> state
              ))
            }.map { _ => state }
          }
        }
      ).recoverWith { case e =>
        fsService.remove(Json.obj("id" -> message.media.id)).map { _ => throw e }
      }

      /**
        * Handles the `DeleteRewardMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the project.
        * @note           Message `DeleteRewardMedia` does not alter the state of the project.
        */
      def apply(message: DeleteRewardMedia): Future[Ztate] = exec(
        message, project => {
          val rewards = project.rewards.getOrElse(Seq.empty)
          if (message.index < 0 || message.index >= rewards.length) {
            Future.failed(ElementNotFound("reward", message.index.toString, "project", message.projectId))
          } else fsService.remove(Json.obj(
            "metadata.projectId" -> project.pseudoid,
            "metadata.rewardId" -> rewards(message.index).id,
            "metadata.state.value" -> state
          )).map {
            case n if n > 0 => Deleted
            case _ => throw ElementNotFound("media of reward", message.index.toString, "project", message.projectId)
          }
        }
      )

      /**
        * Handles the `FindRewardMedia` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the media file of the
        *                 reward, or `None` if it could not be found.
        */
      def apply(message: FindRewardMedia): Future[Option[MetaFile]] = exec(
        message, project => {
          val rewards = project.rewards.getOrElse(Seq.empty)
          if (message.index < 0 || message.index >= rewards.length) {
            Future.failed(ElementNotFound("reward", message.index.toString, "project", message.projectId))
          } else fsService.find(Json.obj(
            "metadata.projectId" -> project.pseudoid,
            "metadata.rewardId" -> rewards(message.index).id,
            "metadata.state.value" -> state
          )).map(_.headOption)
        }
      ).recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `CreateFaq` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a tuple with the new
        *                 state of the project and the zero-based index at which the
        *                 faq was added.
        * @note           Message `CreateFaq` does not alter the state of the project.
        */
      def apply(message: CreateFaq): Future[(Ztate, Int)] = {
        wipService.addFaq(message.projectId, message.faq)(state).map {
          case Some(index) => (state, index)
          case _ => throw StaleObject(message.projectId, context.collectionName)
        }
      }

      /**
        * Handles the `UpdateFaq` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the project.
        * @note           Message `UpdateFaq` does not alter the state of the project.
        */
      def apply(message: UpdateFaq): Future[Ztate] = exec(
        message, project => {
          if (message.index < 0 || message.index >= project.faqs.getOrElse(Seq.empty).length) {
            Future.failed(ElementNotFound("faq", message.index.toString, "project", message.projectId))
          } else {
            wipService.updateFaq(message.projectId, message.index, message.update)(state).map {
              case Some(_) => state
              case _ => throw StaleObject(message.projectId, context.collectionName)
            }
          }
        }
      )

      /**
        * Handles the `DeleteFaq` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the project.
        * @note           Message `DeleteFaq` does not alter the state of the project.
        */
      def apply(message: DeleteFaq): Future[Ztate] = exec(
        message, project => {
          if (message.index < 0 || message.index >= project.faqs.getOrElse(Seq.empty).length) {
            Future.failed(ElementNotFound("faq", message.index.toString, "project", message.projectId))
          } else context.removeFaq(message.projectId, message.index)(state).map {
            case Some(_) => ProjectFsm(project.state.get)
            case _ => throw StaleObject(message.projectId, context.collectionName)
          }
        }
      )

      /**
        * Handles the `FindFaq` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the faq specified
        *                 in `message`, or `None` if it could not be found.
        */
      def apply(message: FindFaq): Future[Option[Faq]] = exec(
        message, project => {
          Future.successful {
            val faqs = project.faqs.getOrElse(Seq.empty)
            if (message.index < 0 || message.index >= faqs.length) None
            else Some(faqs(message.index))
          }
        }
      ).recover {
        case e: NotFoundInState => None
        case e => throw e
      }

      /**
        * Handles the `ListFaqs` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the faqs of the project
        *                 specified in `message`, or an empty `Seq` if the project
        *                 could not be found or no faqs could be found.
        */
      def apply(message: ListFaqs): Future[Seq[Faq]] = exec(
        message, project => {
          Future.successful(project.faqs.getOrElse(Seq.empty))
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `IssuePaymentRequest` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a new invoice.
        */
      def apply(message: IssuePaymentRequest): Future[Invoice] = exec(
        message, project => {
          this.token.asInstanceOf[Option[Token]] match {
            // ensure that the request is authenticated regardless of how security is
            // configured and that the account issuing the payment request is different
            // than the account associated with the project (self-pledging not allowed)
            case Some(token) if token.account.length > 0 && token.account != project.accountId.get =>
              val paymentRequest = message.paymentRequest
              val projectCurrency = project.fundingInfo.get.currency.get

              paymentRequest.accountId = Some(token.account)
              paymentRequest.amount.currency match {
                case paymentCurrency if paymentCurrency != projectCurrency => PayGateway.rates(paymentCurrency, Bid).flatMap { rates =>
                  paymentRequest.amount = Coin(paymentRequest.amount.value * rates(projectCurrency), projectCurrency)
                  PayGateway.issuePaymentRequest(paymentRequest)
                }
                case _ => PayGateway.issuePaymentRequest(paymentRequest)
              }
            case _ => operationNotAllowed(message)
          }
        }
      )

      /**
        * Handles the `ListBackers` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the backers of the project
        *                 specified in `message`, or an empty `Seq` if the project
        *                 could not be found or no backers could be found.
        */
      def apply(message: ListBackers): Future[Seq[User]] = exec(
        message, project => {
          val selectorWrites = (
            (__ \ "projectId").write[String] ~
            (__ \ "state.value").writeNullable[String]
          ).tupled

          for {
            accountIds <- pledgeService.distinct(
              "backerInfo.accountId",
              selectorWrites.writes(project.id.get, if (state == Published) Some(PledgeFsm.Settled) else None)
            )
            backers <- userService.find(
              Json.obj("$in" -> Json.obj("metaAccounts.id" -> Json.toJson(accountIds))),
              Some(Json.obj("$include" -> Json.arr(
                "id", "username", "firstName", "lastName", "company", "website",
                "addresses.state", "addresses.country", "addresses.timeZone"
              ))),
              None,
              message.page, message.perPage
            )
          } yield backers
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `CountBackers` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of backers that pledged
        *                 to the project specified in `message`.
        */
      def apply(message: CountBackers): Future[Int] = exec(
        message, project => {
          pledgeService.distinct(
            "backerInfo.accountId",
            Json.obj("projectId" -> project.id)
          ).map(l => l.length)
        }
      ).recover {
        case e: NotFoundInState => 0
        case e => throw e
      }

      /**
        * Handles the `ListPledges` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the pledges of the project
        *                 specified in `message`, or an empty `Seq` if the project
        *                 could not be found or no pledges could be found.
        */
      def apply(message: ListPledges): Future[Seq[Pledge]] = exec(
        message, project => {
          PledgeFsm(PledgeFsm.Settled) ! PledgeFsm.List(message.projectId, message.page, message.perPage)
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `ListPledgesByState` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the pledges of the project specified
        *                 in `message` in a give state, or an empty `Seq` if the project
        *                 could not be found or no pledges could be found.
        */
      def apply(message: ListPledgesByState): Future[Seq[Pledge]] = exec(
        message, project => {
          PledgeFsm(message.pledgeState) ! PledgeFsm.ListByState(message.projectId, message.page, message.perPage)
        }
      ).recover {
        case e: NotFoundInState => Seq.empty
        case e => throw e
      }

      /**
        * Handles the `AddPledge` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the updated funding info.
        * @note           Message `AddPledge` does not alter the state of the project.
        */
      def apply(message: AddPledge): Future[FundingInfo] = {
        def addPledge(refId: RefId, accountId: String, pledgedAmount: Coin, identityMode: IdentityMode): Future[FundingInfo] = {
          val receivedAmount = message.transaction.amount
          implicit val precision = Precision(0.01)

          // round half-up received amount
          val receivedValue = { receivedAmount.refCurrency match {
            case Some(refCurrency) if refCurrency != PayGateway.Cryptocurrency => (receivedAmount.value * receivedAmount.rate.get)
            case _ => receivedAmount.value
          }} ~~

          // round half-up pledged amount
          val pledgedValue = { pledgedAmount.refCurrency match {
            case Some(refCurrency) if refCurrency != PayGateway.Cryptocurrency => (pledgedAmount.value * pledgedAmount.rate.get)
            case _ => pledgedAmount.value
          }} ~~

          // increase raised amount and select reward
          implicit val ztate = this.state; (
            context.incRaisedAmount(refId.value, receivedValue) zip
            context.selectRewardByPledgeAmount(refId.value, pledgedValue)
          ).flatMap {
            case (Some(fundingInfo), reward) => (PledgeFsm(PledgeFsm.New) ! PledgeFsm.Save(
              Pledge(
                projectId = Some(refId.value),
                rewardId = reward.flatMap(_.id),
                backerInfo = Some(BackerInfo(accountId, identityMode)),
                amount = Some(Coin(receivedValue, receivedAmount.refCurrency.get))
              )
            )).map { _ =>
              // notify originator and backer the pledge has arrived
              context.find(refId.value).foreach { _.foreach { project => (
                userService.findByAccountId(project.accountId, false) zip
                userService.findByAccountId(accountId, false)).foreach {
                  case (Some(originator), Some(backer)) =>
                    EmailHelper.originator.sendReceivedPledgeNotificationEmail(originator, backer, project, receivedAmount)
                    EmailHelper.backer.sendPledgeReceivedNotificationEmail(backer, originator, project, receivedAmount)
                  case _ => // should never happen
                }
              }}

              // return updated funding info
              fundingInfo
            }
            case _ => Future.failed(NotFoundInState("project", refId.value, state))
          }
        }

        orderService.findRoot(message.transaction.orderId).flatMap {
          case Some(order) => order.refId match {
            case Some(refId) => refId.domain match {
              case domain if domain == "projects" =>
                val issuerIdentityMode = order.issuerIdentityMode getOrElse Username
                addPledge(refId, order.accountId.get, order.amount.get, issuerIdentityMode)
              case _ => Future.failed(UnreferencedObject("payment request order", message.transaction.orderId.getOrElse("?")))
            }
            case _ => Future.failed(UnreferencedObject("payment request order", message.transaction.orderId.getOrElse("?")))
          }
          case _ => Future.failed(NotFound("payment request order", message.transaction.orderId.getOrElse("?")))
        }
      }

      /**
        * Handles the `SellCoins` message.
        *
        * @param message  The message to handle.
        * @note           Message `SellCoins` does not alter the state of the project.
        */
      def apply(message: SellCoins): Future[Unit] = {
        orderService.find(message.transaction.orderId).flatMap {
          case Some(order) => PayGateway.sellCoins(
            order.accountId.getOrElse(accountId("core")),
            message.transaction.amount,
            order.refId,
            order.id
          ).map { _ => Unit }
          case _ => Future.failed(NotFound("sell order", message.transaction.orderId.getOrElse("?")))
        }
      }

      /**
        * Handles the `SendCoins` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the project.
        */
      def apply(message: SendCoins): Future[Ztate] = {
        def sendCoins(refId: RefId): Future[Ztate] = {
          def notifyBackers(project: Project, originator: User, page: Int): Future[Unit] = {
            pledgeService.find(Json.obj("projectId" -> project.id), None, None, page, DefaultPerPage).flatMap {
              case pledges if pledges.nonEmpty => pledges.map { pledge =>
                userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                  EmailHelper.backer.sendFundingConfirmationEmail(backer, originator, project)
                }}}
                notifyBackers(project, originator, page + 1)
              case _ => Future.successful(Unit)
            }
          }

          context.findAndUpdate(
            Json.obj(refId.name -> refId.value, "state.value" -> state),
            Json.obj("state" -> State(Funded)), None
          ).flatMap {
            case Some(project) => (PledgeFsm(PledgeFsm.Granted) ! PledgeFsm.CashIn(project.id.get)).flatMap { _ =>
              userService.findByAccountId(project.accountId, false).map { user => user.foreach { originator =>
                PayGateway.sendCoins(
                  accountId("core"),
                  message.transaction.amount,
                  project.fundingInfo.get.coinAddress.get,
                  refId,
                  message.transaction.orderId
                ).foreach { _ =>
                  EmailHelper.originator.sendFundingConfirmationEmail(originator, project, message.transaction.amount)
                  notifyBackers(project, originator, 0)
                }
              }; Funded }.recoverWith {
                // rollback pledges state and forward error
                case e => (PledgeFsm(PledgeFsm.Settled) ! PledgeFsm.Grant(project.id.get)).map { throw e }
              }
            }
            case _ => Future.failed(NotFoundInState("project", refId.value, state))
          }
        }

        orderService.find(message.transaction.orderId).flatMap {
          case Some(order) => order.refId match {
            case Some(refId) if refId.domain == "projects" => sendCoins(refId)
            case Some(refId) if refId.domain == "pledges" => PayGateway.transferCoins(
              accountId("core"),
              message.transaction.amount,
              order.refId,
              message.transaction.orderId).map { _ => Closed /* project already closed */ }
            case _ => Future.failed(UnreferencedObject("order", message.transaction.orderId.getOrElse("?")))
          }
          case _ => Future.failed(NotFound("payment order", message.transaction.orderId.getOrElse("?")))
        }
      }

      /**
        * Handles the `ReturnCoins` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of pledges refunded.
        * @note           Message `ReturnCoins` does not alter the state of the project.
        */
      def apply(message: ReturnCoins): Future[Int] = {
        (PledgeFsm(PledgeFsm.AwaitingRefund) ! PledgeFsm.ReturnCoins(message.transaction))
      }

      /**
        * Handles the `UnlistExpired` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of projects unlisted.
        * @note           Message `UnlistExpired` sets the state of expired projects to
        *                 either `Succeeded` or `Closed`, depending on whether or not
        *                 funding has been granted.
        */
      def apply(message: UnlistExpired): Future[Int] = {
        val now = DateTime.now(DateTimeZone.UTC).getMillis

        def unlistExpiredProjects(fundingModel: Option[(LogisticRegression, Seq[String], Int)], count: Int): Future[Int] = {
          for {
            // get expired projects in chunk of `DefaultPerPage`
            projects <- context.find(
              Json.obj(
                "state.value" -> state,
                "$lt" -> Json.obj("state.timestamp" -> now, "fundingInfo.endTime" -> now)
              ),
              Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs"))),
              None, 0, DefaultPerPage
            )
            projectCount <- projects.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                model <- if (fundingModel.isDefined) Future.successful(fundingModel) else getFundingModel
                _ <- Future.sequence(projects.map { project =>
                  for {
                    orderCount <- orderService.count(Json.obj(
                      "refId.domain" -> "projects",
                      "refId.name" -> "id",
                      "refId.value" -> project.id,
                      "status.value" -> Pending
                    ))
                    projectState <- orderCount match {
                      case n if n == 0 => evaluateFunding(project, model)
                      case _ => setState(project, AwaitingOrdersCompletion, None).flatMap { newState =>
                        fsService.update(
                          Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
                          Json.obj("metadata.state" -> State(newState))
                        )
                      }
                    }
                  } yield projectState
                })

                // invoke unlistExpiredProjects recursively until there are no projects left
                projectCount <- projects.length match {
                  case l if l == DefaultPerPage => unlistExpiredProjects(model, count + l)
                  case _ => Future.successful(count)
                }
              } yield projectCount
            }
          } yield projectCount
        }

        unlistExpiredProjects(None, 0)
      }

      /**
        * Handles the `TakeUpAwaitingOrdersCompletion` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of projects processed.
        */
      def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = {
        val now = DateTime.now(DateTimeZone.UTC).getMillis

        def processAwatingProjects(fundingModel: Option[(LogisticRegression, Seq[String], Int)], count: Int): Future[Int] = {
          for {
            // get awaiting projects in chunk of `DefaultPerPage`
            projects <- context.find(
              Json.obj(
                "state.value" -> state,
                "$lt" -> Json.obj("state.timestamp" -> now)
              ),
              Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history"))),
              None, 0, DefaultPerPage
            )
            projectCount <- projects.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                model <- if (fundingModel.isDefined) Future.successful(fundingModel) else getFundingModel
                projectStates <- Future.sequence(projects.map { project =>
                  for {
                    orderCount <- orderService.count(Json.obj(
                      "refId.domain" -> "projects",
                      "refId.name" -> "id",
                      "refId.value" -> project.id,
                      "status.value" -> Pending
                    ))
                    projectState <- orderCount match {
                      case n if n == 0 => evaluateFunding(project, model)
                      case _ => Future.successful(state) // do nothing and keep current state
                    }
                  } yield projectState
                })

                // invoke processAwaitingProjects recursively until there are no projects left
                projectCount <- projects.length match {
                  case l if l == DefaultPerPage && l > projectStates.filter(_ != state).length => processAwatingProjects(model, count + l)
                  case _ => Future.successful(count)
                 }
              } yield projectCount
            }
          } yield projectCount
        }

        processAwatingProjects(None, 0)
      }

      /**
        * Handles the `PollSucceeded` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of projects polled.
        * @note           Message `PollSucceeded` does not alter the state of the projects.
        */
      def apply(message: PollSucceeded): Future[Int] = {
        val dt = DateTime.now(DateTimeZone.UTC)
        val now = dt.getMillis
        val dateTime = dt.minusMinutes(CashInPeriod).getMillis

        def pollSucceededProjects(page: Int, count: Int): Future[Int] = {
          for {
            // get succeeded projects in chunk of `DefaultPerPage`
            projects <- context.find(
              Json.obj(
                "state.value" -> state,
                "$lt" -> Json.obj("state.timestamp" -> now),
                "$gte" -> Json.obj("fundingInfo.endTime" -> dateTime)
              ),
              Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history"))),
              None, page, DefaultPerPage
            )
            projectCount <- projects.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                _ <- Future.sequence(projects.map { project =>
                  userService.findByAccountId(project.accountId, false).map { _.foreach { originator =>
                    EmailHelper.originator.sendCashInReminderEmail(originator, project, CashInPeriod)
                  }}
                })

                // invoke pollSucceededProjects recursively until there are no projects left
                projectCount <- projects.length match {
                  case l if l == DefaultPerPage => pollSucceededProjects(page + 1, count + l)
                  case _ => Future.successful(count)
                 }
              } yield projectCount
            }
          } yield projectCount
        }

        pollSucceededProjects(0, 0)
      }

      /**
        * Handles the `PullMissed` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the number of missed projects
        *                 for which funding has been revoked.
        */
      def apply(message: PullMissed): Future[Int] = {
        val dt = DateTime.now(DateTimeZone.UTC)
        val now = dt.getMillis
        val dateTime = dt.minusMinutes(CashInPeriod).getMillis

        def pullMissedProjects(count: Int): Future[Int] = {
          for {
            // get missed projects in chunk of `DefaultPerPage`
            projects <- context.find(
              Json.obj(
                "state.value" -> state,
                "$lt" -> Json.obj("state.timestamp" -> now, "fundingInfo.endTime" -> dateTime)
              ),
              Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history"))),
              None, 0, DefaultPerPage
            )
            projectCount <- projects.isEmpty match {
              case true => Future.successful(count)
              case false => for {
                _ <- Future.sequence(projects.map(close(_)))

                // invoke pullMissedProjects recursively until there are no projects left
                projectCount <- projects.length match {
                  case l if l == DefaultPerPage => pullMissedProjects(count + l)
                  case _ => Future.successful(count)
                 }
              } yield projectCount
            }
          } yield projectCount
        }

        pullMissedProjects(0)
      }

      /**
        * Returns a funding model calculated with statistics collected so far.
        * @return A `Future` containing a trained logistic regression algorithm.
        */
      private def getFundingModel: Future[Option[(LogisticRegression, Seq[String], Int)]] = {
        algorithmService.find(
          Json.obj("filename" -> "funding-model.dat")
        ).flatMap { _.headOption match {
          case Some(file) => 
            algorithmService.enumerate(file) |>>> Iteratee.fold(ArrayBuilder.make[Byte]()) { (result, arr: Array[Byte]) =>
              result ++= arr
            }.map { arrayBuilder =>
              var ranges: Seq[String] = Seq.empty
              var scaling: Int = 1
              file.metadata.foreach { metadata =>
                ranges = (metadata \ "classes").as[Seq[String]]
                scaling = (metadata \ "scaling").as[Int]
              }
              Some((LogisticRegression(arrayBuilder.result), ranges, scaling))
            }
          case _ => Future.successful(None)
        }}
      }

      /**
        * Evaluates whether or not to fund the specified [[Project]].
        *
        * @param project      The [[Project]] to evaluate for funding.
        * @param fundingModel An `Option` value containing the trained funding model,
        *                     or `None` if the model has not been trained yet.
        * @return             A `Future` value containing the new state of the project.
        */
      private def evaluateFunding(
        project: Project,
        fundingModel: Option[(LogisticRegression, Seq[String], Int)]
      ): Future[Ztate] = {
        project.fundingInfo match {
          case Some(fundingInfo) =>
            val targetAmount = fundingInfo.targetAmount.getOrElse(0.0)
            val raisedAmount = fundingInfo.raisedAmount.getOrElse(0.0)

            if (raisedAmount > 0.0 && raisedAmount >= targetAmount) {
              grantFunding(project, Some("Target amount reached"))
            } else { fundingModel match {
              case Some((logisticRegression, ranges, scaling)) =>
                var range = ranges.filter { range =>
                  val values = range.split("-").map(parse[Double](_).get)
                  targetAmount >= values(0) && (targetAmount <= values(1) || 8.0 == values(1))
                } head

                val observation = FundraisingObservation(
                  range, fundingInfo.duration.getOrElse(0).asInstanceOf[Double],
                  targetAmount, raisedAmount, 0, scaling
                )

                val scores = logisticRegression.classify(observation)
                //  scores._1: probability of not funding
                //  scores._2: probability of funding
                if (scores._1 > scores._2) close(project, Some("Raised amount below target and funding score too low"))
                else grantFunding(project, Some("Raised amount below target but funding score high enough"), true)
              case _ => close(project, Some("Target amount not reached"))
            }}
          case _ => close(project, Some("No funding info")) // should never happen
        }
      }

      /**
        * Grants funding to the specified [[Project]].
        *
        * @param project  The [[Project]] to grant funding to.
        * @param comment  The comment about the operation, if any.
        * @param byScore  A Boolean value indicating whether or not funding is granted by score.
        * @return         A `Future` value containing the new state of the project.
        */
      private def grantFunding(project: Project, comment: Option[String] = None, byScore: Boolean = false): Future[Ztate] = {
        val notifyOriginator: (User, Project, Int) => Unit = if (byScore) EmailHelper.originator.sendGrantedByScoreNotificationEmail
        else EmailHelper.originator.sendTargetHitNotificationEmail

        val notifyBacker: (User, User, Project, Int) => Unit = if (byScore) EmailHelper.backer.sendGrantedByScoreNotificationEmail
        else EmailHelper.backer.sendTargetHitNotificationEmail

        def notifyBackers(originator: User, page: Int): Future[Unit] = {
          pledgeService.find(Json.obj("projectId" -> project.id), None, None, page, DefaultPerPage).flatMap {
            case pledges if pledges.nonEmpty => pledges.foreach { pledge =>
              userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                notifyBacker(backer, originator, project, CashInPeriod)
              }}}
              notifyBackers(originator, page + 1)
            case _ => Future.successful(Unit)
          }
        }

        project.fundingInfo match {
          case Some(fundingInfo) => setState(
              project, Succeeded, comment.map(comment => HistoryEvent("Granted funding", Some(comment)))
            ).flatMap { newState =>
              PayGateway.enablePayout(project.id.get, fundingInfo.coinAddress.get).flatMap { _ =>
                fsService.update(
                  Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
                  Json.obj("metadata.state" -> State(newState))
                ).flatMap { _ => (PledgeFsm(PledgeFsm.Settled) ! PledgeFsm.Grant(project.id.get)).flatMap { _ =>
                  userService.findByAccountId(project.accountId, false).map { _.foreach { originator =>
                    notifyOriginator(originator, project, CashInPeriod)
                    notifyBackers(originator, 0)
                  }}
                }}.map { _ => newState }
              }.recoverWith {
                // rollback project state and forward error
                case e => setState(project, this.state, None).map { throw e }
              }
            }
          case _ => Future.failed(TransductionNotPossible(
            "project", project.id.get, Succeeded,
            "no funding info"
          ))
        }
      }

      /**
        * Closes the specified [[Project]].
        *
        * @param project  The [[Project]] to close.
        * @param comment  The comment about the operation, if any.
        * @return         A `Future` value containing the new state of the project.
        */
      private def close(project: Project, comment: Option[String] = None): Future[Ztate] = {
        def notifyBackers(originator: User, page: Int): Future[Unit] = {
          pledgeService.find(Json.obj("projectId" -> project.id), None, None, page, DefaultPerPage).flatMap {
            case pledges if pledges.nonEmpty => pledges.foreach { pledge =>
              userService.findByAccountId(pledge.backerInfo.get.accountId, false).map { _.foreach { backer =>
                state match {
                  case Published => EmailHelper.backer.sendTargetMissedNotificationEmail(backer, project, RefundPeriod)
                  case Succeeded => EmailHelper.backer.sendCashInPeriodExpiredNotificationEmail(backer, originator, project, RefundPeriod)
                }
              }}}
              notifyBackers(originator, page + 1)
            case _ => Future.successful(Unit)
          }
        }

        val title = state match {
          case Published => "Funding target not reached"
          case Succeeded => "Cash-in period expired"
          case _ => "Closed"
        }

        setState(project, Closed, comment.map(
          comment => HistoryEvent(title, Some(comment))
        )).flatMap { newState =>
          fsService.update(
            Json.obj("metadata.projectId" -> project.pseudoid, "metadata.state.value" -> state),
            Json.obj("metadata.state" -> State(newState))
          ).flatMap { _ =>
            userService.findByAccountId(project.accountId, false).map { _.foreach { originator =>
              state match {
                case Published => (PledgeFsm(PledgeFsm.Settled) ! PledgeFsm.Revoke(project.id.get)).map { _ =>
                  EmailHelper.originator.sendTargetMissedNotificationEmail(originator, project)
                }
                case Succeeded => (PledgeFsm(PledgeFsm.Granted) ! PledgeFsm.Revoke(project.id.get)).map { _ =>
                  EmailHelper.originator.sendCashInPeriodExpiredNotificationEmail(originator, project)
                }
              }
              notifyBackers(originator, 0)
            }}.map { _ => newState }
          }
        }
      }

      /**
        * Determines whether or not the specified amount corresponds at least
        * to the minimum amount in USD defined in the application configuration.
        *
        * @param amount The amount to check.
        */
      private def checkAmount(amount: Coin): Future[Unit] = {
        import services.pay.PayErrors._
        import PayGateway._

        rates(amount.currency, Bid).map { rates =>
          val usdRate = rates(SupportedCurrencies(USD))
          (amount.value * usdRate) match {
            case value if value < MinAmount => throw AmountTooLow("target", amount.currency, amount.value, MinAmount / usdRate)
            case _ => 
          }
        }
      }
    }

    /**
      * Disables any conversion that is not allowed from state `New`.
      */
    class FromNew extends Transduction {

      def state = New
      def context = wipService

      // override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      override def apply(message: ListPledges): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: ListPledgesByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Open`.
      */
    class FromOpen extends Transduction {

      def state = Open
      def context = wipService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      // override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      // override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      // override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      // override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      // override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Submitted`.
      */
    class FromSubmitted extends Transduction {

      def state = Submitted
      def context = wipService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Audit`.
      */
    class FromAudit extends Transduction {

      def state = Audit
      def context = wipService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Published`.
      */
    class FromPublished extends Transduction {

      // messages enabled to anybody
      authorizeImpl("Find") = (accountId: String, token: Token) => true
      authorizeImpl("List") = (accountId: String, token: Token) => true
      authorizeImpl("ListRandom") = (accountId: String, token: Token) => true
      authorizeImpl("Count") = (accountId: String, token: Token) => true
      authorizeImpl("FindMedia") = (accountId: String, token: Token) => true
      authorizeImpl("ListMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindReward") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardById") = (accountId: String, token: Token) => true
      authorizeImpl("ListRewards") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindFaq") = (accountId: String, token: Token) => true
      authorizeImpl("ListFaqs") = (accountId: String, token: Token) => true
      authorizeImpl("ListBackers") = (accountId: String, token: Token) => true
      authorizeImpl("CountBackers") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledges") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledgesByState") = (accountId: String, token: Token) => true

      def state = Published
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      // override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      // override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      // override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
       // override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `AwaitingOrdersCompletion`.
      */
    class FromAwaitingOrdersCompletion extends Transduction {

      def state = AwaitingOrdersCompletion
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      // override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `AwaitingCoins`.
      */
    class FromAwaitingCoins extends Transduction {

      // messages enabled to anybody
      authorizeImpl("Find") = (accountId: String, token: Token) => true
      authorizeImpl("List") = (accountId: String, token: Token) => true
      authorizeImpl("Count") = (accountId: String, token: Token) => true
      authorizeImpl("FindMedia") = (accountId: String, token: Token) => true
      authorizeImpl("ListMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindReward") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardById") = (accountId: String, token: Token) => true
      authorizeImpl("ListRewards") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindFaq") = (accountId: String, token: Token) => true
      authorizeImpl("ListFaqs") = (accountId: String, token: Token) => true
      authorizeImpl("ListBackers") = (accountId: String, token: Token) => true
      authorizeImpl("CountBackers") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledges") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledgesByState") = (accountId: String, token: Token) => true

      def state = AwaitingCoins
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Succeeded`.
      */
    class FromSucceeded extends Transduction {

      // messages enabled to anybody
      authorizeImpl("Find") = (accountId: String, token: Token) => true
      authorizeImpl("List") = (accountId: String, token: Token) => true
      authorizeImpl("Count") = (accountId: String, token: Token) => true
      authorizeImpl("FindMedia") = (accountId: String, token: Token) => true
      authorizeImpl("ListMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindReward") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardById") = (accountId: String, token: Token) => true
      authorizeImpl("ListRewards") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindFaq") = (accountId: String, token: Token) => true
      authorizeImpl("ListFaqs") = (accountId: String, token: Token) => true
      authorizeImpl("ListBackers") = (accountId: String, token: Token) => true
      authorizeImpl("CountBackers") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledges") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledgesByState") = (accountId: String, token: Token) => true

      def state = Succeeded
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      // override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      // override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      // override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Funded`.
      */
    class FromFunded extends Transduction {

      // messages enabled to anybody
      authorizeImpl("Find") = (accountId: String, token: Token) => true
      authorizeImpl("List") = (accountId: String, token: Token) => true
      authorizeImpl("Count") = (accountId: String, token: Token) => true
      authorizeImpl("FindMedia") = (accountId: String, token: Token) => true
      authorizeImpl("ListMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindReward") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardById") = (accountId: String, token: Token) => true
      authorizeImpl("ListRewards") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindFaq") = (accountId: String, token: Token) => true
      authorizeImpl("ListFaqs") = (accountId: String, token: Token) => true
      authorizeImpl("ListBackers") = (accountId: String, token: Token) => true
      authorizeImpl("CountBackers") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledges") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledgesByState") = (accountId: String, token: Token) => true

      def state = Funded
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Closed`.
      */
    class FromClosed extends Transduction {

      // messages enabled to anybody
      authorizeImpl("Find") = (accountId: String, token: Token) => true
      authorizeImpl("List") = (accountId: String, token: Token) => true
      authorizeImpl("Count") = (accountId: String, token: Token) => true
      authorizeImpl("FindMedia") = (accountId: String, token: Token) => true
      authorizeImpl("ListMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindReward") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardById") = (accountId: String, token: Token) => true
      authorizeImpl("ListRewards") = (accountId: String, token: Token) => true
      authorizeImpl("FindRewardMedia") = (accountId: String, token: Token) => true
      authorizeImpl("FindFaq") = (accountId: String, token: Token) => true
      authorizeImpl("ListFaqs") = (accountId: String, token: Token) => true
      authorizeImpl("ListBackers") = (accountId: String, token: Token) => true
      authorizeImpl("CountBackers") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledges") = (accountId: String, token: Token) => true
      authorizeImpl("ListPledgesByState") = (accountId: String, token: Token) => true

      def state = Closed
      def context = daoService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Rejected`.
      */
    class FromRejected extends Transduction {

      def state = Rejected
      def context = wipService

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      // override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      // override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      // override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      // override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      // override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      // override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      // override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      // override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      // override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      // override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      // override def apply(message: ListPledges): Future[Seq[Pledges]] = operationNotAllowed(message)
      // override def apply(message: ListPledgesByState): Future[Seq[Pledges]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Deleted`.
      */
    class FromDeleted extends Transduction {

      def state = Deleted
      def context = null

      override def apply(message: Save): Future[(Ztate, Project)] = operationNotAllowed(message)
      override def apply(message: Update): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ChangeCoinAddress): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Submit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForAudit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Publish): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Reject): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Edit): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Relist): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefundPledges): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SetShippingInfo): Future[Int] = operationNotAllowed(message)
      override def apply(message: GrantFunding): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Fund): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RewardPledge): Future[PledgeFsm.Ztate] = operationNotAllowed(message)
      override def apply(message: Close): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Delete): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: Pick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Unpick): Future[Unit] = operationNotAllowed(message)
      override def apply(message: Find): Future[Option[Project]] = operationNotAllowed(message)
      override def apply(message: List): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: ListRandom): Future[Seq[Project]] = operationNotAllowed(message)
      override def apply(message: Count): Future[Int] = operationNotAllowed(message)
      override def apply(message: GetHistory): Future[Seq[HistoryEvent]] = operationNotAllowed(message)
      override def apply(message: AddMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: ListMedia): Future[Seq[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateReward): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteReward): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindReward): Future[Option[Reward]] = operationNotAllowed(message)
      override def apply(message: FindRewardById): Future[Option[Reward]] = operationNotAllowed(message)
      override def apply(message: ListRewards): Future[Seq[Reward]] = operationNotAllowed(message)
      override def apply(message: AddRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteRewardMedia): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindRewardMedia): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: CreateFaq): Future[(Ztate, Int)] = operationNotAllowed(message)
      override def apply(message: UpdateFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteFaq): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindFaq): Future[Option[Faq]] = operationNotAllowed(message)
      override def apply(message: ListFaqs): Future[Seq[Faq]] = operationNotAllowed(message)
      override def apply(message: IssuePaymentRequest): Future[Invoice] = operationNotAllowed(message)
      override def apply(message: ListBackers): Future[Seq[User]] = operationNotAllowed(message)
      override def apply(message: CountBackers): Future[Int] = operationNotAllowed(message)
      override def apply(message: ListPledges): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: ListPledgesByState): Future[Seq[Pledge]] = operationNotAllowed(message)
      override def apply(message: AddPledge): Future[FundingInfo] = operationNotAllowed(message)
      override def apply(message: SellCoins): Future[Unit] = operationNotAllowed(message)
      override def apply(message: SendCoins): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ReturnCoins): Future[Int] = operationNotAllowed(message)
      override def apply(message: UnlistExpired): Future[Int] = operationNotAllowed(message)
      override def apply(message: TakeUpAwaitingOrdersCompletion): Future[Int] = operationNotAllowed(message)
      override def apply(message: PollSucceeded): Future[Int] = operationNotAllowed(message)
      override def apply(message: PullMissed): Future[Int] = operationNotAllowed(message)
    }
  }
}
