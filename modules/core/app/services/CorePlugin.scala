/*#
  * @file CorePlugin.scala
  * @begin 24-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import play.api.{Application, Plugin}
import play.api.libs.json._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import utils.common.env._
import services.pay.PayGateway._
import models.pay._
import models.core._

/**
  * Implements the `core` plugin.
  *
  * @constructor  Initializes a new instance of the `CorePlugin` class.
  * @param app    The current application.
  */
class CorePlugin(app: Application) extends Plugin {

  import scala.concurrent.duration._
  import akka.actor.Cancellable
  import CorePlugin._

  @inline private val RunProcessableInterval = 15
  private val RemindInterval = configuration.getInt("core.project.remindInterval").getOrElse(1440)

  private var cancellableRunProcessable: Option[Cancellable] = None
  private var cancellablePollRemindable: Option[Cancellable] = None

  /**
    * Called when the application starts
    */
  override def onStart = {
    def _runProcessable: Future[Unit] = {
      val f = for {
        _ <- unlistExpiredProjects
        _ <- takeUpAwaitingProjects // awaiting pending orders completion
        _ <- takeUpAwaitingPledges  // awaiting refund threshold
      } yield Unit

      f.map { _ => cancellableRunProcessable = Some(
        Akka.system.scheduler.scheduleOnce(RunProcessableInterval.minutes)(_runProcessable)
      )}
    }

    def _pollRemindable: Future[Unit] = {
      val f = for {
        _ <- pollSucceededProjects
        _ <- pollRevokedPledges
      } yield Unit

      f.map { _ => cancellablePollRemindable = Some(
        Akka.system.scheduler.scheduleOnce(RemindInterval.minutes)(_pollRemindable)
      )}
    }

    messageBroker.createConsumer(XchangeDeposit, sellCoins)
    messageBroker.createConsumer(XchangeDepositOnTrade, sendCoins)
    messageBroker.createConsumer(XchangeWithdrawalOnTrade, addPledge)
    messageBroker.createConsumer(LocalDeposit, returnCoins)

    cancellableRunProcessable = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_runProcessable))
    cancellablePollRemindable = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_pollRemindable))

    Logger.info("CorePlugin started")
  }

  /**
    * Called when the application stops.
    */
  override def onStop = {
    cancellableRunProcessable.foreach(_.cancel)
    cancellablePollRemindable.foreach(_.cancel)

    Logger.info("CorePlugin stopped")
  }
}

object CorePlugin {

  import scala.util.control.NonFatal
  import services.core.projects.ProjectFsm
  import services.core.pledges.PledgeFsm

  private var pullUnclaimedNext = false

  /**
    * Sells coins pledged to a project and deposits resulting fiat to bank account.
    * @param json The deposit transaction as JSON.
    */
  def sellCoins(json: JsValue): Future[Unit] = {
    val transaction = json.as[Transaction]
    (ProjectFsm.Published ! ProjectFsm.SellCoins(transaction)).map { _ =>
      val amount = transaction.amount
      Logger.info(s"sold coins for an amount of ${amount.currency} ${amount.value}")
    }.recover {
      case NonFatal(e) => Logger.error("error selling coins", e)
    }
  }

  /**
    * Sends coins to originator in case of funding (cash-in) or to local wallet in case of refund.
    * @param json The deposit on trade transactions as JSON.
    */
  def sendCoins(json: JsValue): Future[Unit] = {
    zipTransactions(json.as[JsArray].as[List[Transaction]]) match {
      case Some(transaction) =>
        (ProjectFsm.AwaitingCoins ! ProjectFsm.SendCoins(transaction)).map {
          case state if state == ProjectFsm.Funded =>
            val amount = transaction.amount
            Logger.info(s"sent coins for an amount of ${amount.currency} ${amount.value}")
          case _ =>
        }.recover {
          case NonFatal(e) => Logger.error("error sending coins", e)
        }
      case _ => Future.successful(Unit)
    }
  }

  /**
    * Adds a new pledge after coins received from a backer have been sold.
    * @param json The withdrawal on trade transactions as JSON.
    */
  def addPledge(json: JsValue): Future[Unit] = {
    zipTransactions(json.as[JsArray].as[List[Transaction]]) match {
      case Some(transaction) =>
        (ProjectFsm.Published ! ProjectFsm.AddPledge(transaction)).map { _ =>
          val amount = transaction.amount
          val currency = amount.refCurrency.getOrElse(amount.currency)
          val fiat = amount.value * amount.rate.getOrElse(1.0)
          Logger.info(s"pledged coins for an amount of ${currency} ${fiat}")
        }.recover {
          case NonFatal(e) => Logger.error("error pledging coins", e)
        }
      case _ => Future.successful(Unit)
    }
  }

  /**
    * Returns coins to backers in case of refund.
    * @param json The local deposit transaction as JSON.
    */
  def returnCoins(json: JsValue): Future[Unit] = {
    val transaction = json.as[Transaction]
    (ProjectFsm.Closed ! ProjectFsm.ReturnCoins(transaction)).map { n =>
      if (n > 0) {
        val amount = transaction.amount
        val fees = n * transactionFee
        Logger.info(s"""refunded $n pledge${if (n > 1) "s" else ""} for an amount of ${amount.currency} ${amount.value - fees}""")
      }
    }.recover {
      case NonFatal(e) => Logger.error("error refunding revoked pledges", e)
    }
  }

  /**
    * Unlists projects whose fundraising period is over.
    */
  def unlistExpiredProjects: Future[Unit] = {
    (ProjectFsm.Published ! ProjectFsm.UnlistExpired()).map { n =>
      if (n > 0) Logger.info(s"""unlisted $n expired project${if (n > 1) "s" else ""}""")
    }.recover {
      case NonFatal(e) => Logger.error("error unlisting expired projects", e)
    }
  }

  /**
    * Takes up awaiting projects that no longer have pending orders.
    */
  def takeUpAwaitingProjects: Future[Unit] = {
    (ProjectFsm.AwaitingOrdersCompletion ! ProjectFsm.TakeUpAwaitingOrdersCompletion()).map { n =>
    if (n > 0) Logger.info(s"""processed $n project${if (n > 1) "s" else ""} that no longer ${if (n > 1) "have" else "has"} pending orders""")
    }.recover {
      case NonFatal(e) => Logger.error("error processing projects that no longer have pending orders", e)
    }
  }

  /**
    * Polls for succeeded projects whose originators have not cashed-in yet.
    */
  def pollSucceededProjects: Future[Unit] = {
    (ProjectFsm.Succeeded ! ProjectFsm.PollSucceeded()).flatMap { _ =>
      // use current task to pull possible missed projects
      (ProjectFsm.Succeeded ! ProjectFsm.PullMissed()).map { n =>
        if (n > 0) Logger.info(s"""pulled $n missed project${if (n > 1) "s" else ""}""")
      }
    }.recover {
      case NonFatal(e) => Logger.error("error polling succeeded projects", e)
    }
  }

  /**
    * Polls for revoked pledges whose backers have not got refunded yet.
    */
  def pollRevokedPledges: Future[Unit] = {
    pullUnclaimedNext match {
      case false => (PledgeFsm.Revoked ! PledgeFsm.PollRevoked()).map { _ =>
        pullUnclaimedNext = true
      }.recover {
        case NonFatal(e) => Logger.error("error polling revoked pledges", e)
      }
      case true => (PledgeFsm.Revoked ! PledgeFsm.PullUnclaimed()).map { n =>
        pullUnclaimedNext = false
        if (n > 0) Logger.info(s"""pulled $n unclaimed pledge${if (n > 1) "s" else ""}""")
      }.recover {
        case NonFatal(e) => Logger.error("error pulling unclaimed pledges", e)
      }
    }
  }

  /**
    * Takes up awaiting pledges given that refund threshold has been reached.
    */
  def takeUpAwaitingPledges: Future[Unit] = {
    (PledgeFsm.AwaitingRefundThreshold ! PledgeFsm.TakeUpAwaitingRefundThreshold()).map { n =>
      if (n > 0) Logger.info(s"""processed $n pledge${if (n > 1) "s" else ""} awaiting refund threshold""")
    }.recover {
      case NonFatal(e) => Logger.error("error processing pledges awaiting refund threshold", e)
    }
  }

  /**
    * Combines the specified transactions into a single transaction.
    *
    * @param transactions The transactions to be combined.
    * @return   An `Option` containing the result of combining `transactions`,
    *           or `None` if `transactions` is empty.
    * @note     All input transactions are associated with the same order.
    */
  private def zipTransactions(transactions: List[Transaction]): Option[Transaction] = {
    var totalAmount, totalFee, avgRate = 0.0

    transactions.foreach { transaction =>
      totalAmount += transaction.amount.value
      totalFee += transaction.fee.map(_.value).getOrElse(0.0)
      avgRate += transaction.amount.rate.getOrElse(0.0)
    }

    transactions.length match {
      case length if length > 0 =>
        avgRate = avgRate / length
        Some(Transaction(
          None,
          transactions(0).orderId,
          transactions(0).transactionType,
          None, None,
          Coin(
            totalAmount,
            transactions(0).amount.currency,
            transactions(0).amount.refCurrency,
            Some(avgRate)
          ),
          Some(Coin(
            totalFee,
            transactions(0).amount.currency,
            transactions(0).amount.refCurrency,
            Some(avgRate)
          )),
          Some(DateTime.now(DateTimeZone.UTC))
        ))
      case _ => None
    }
  }
}
