/*#
  * @file PayPlugin.scala
  * @begin 22-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import scala.concurrent.Future
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import play.api.{Application, Plugin}
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Implements the `pay` plugin.
  *
  * @constructor  Initializes a new instance of the `PayPlugin` class.
  * @param app    The current application.
  */
class PayPlugin(app: Application) extends Plugin {

  import scala.concurrent.duration._
  import akka.actor.Cancellable
  import PayPlugin._

  @inline private val RunProcessableInterval = 10
  @inline private val ExpireOutdatedInterval = 30
  @inline private val DiscardExpiredInterval = 24

  private var cancellableRunProcessable: Option[Cancellable] = None
  private var cancellableExpireOutdated: Option[Cancellable] = None
  private var cancellableDiscardExpired: Option[Cancellable] = None

  /**
    * Called when the application starts
    */
  override def onStart = {
    def _runProcessable: Future[Unit] = {
      processTradeOrders.map { _ => cancellableRunProcessable = Some(
        Akka.system.scheduler.scheduleOnce(RunProcessableInterval.minutes)(_runProcessable)
      )}
    }

    def _expireOutdated: Future[Unit] = {
      expirePaymentRequests.map { _ => cancellableExpireOutdated = Some(
        Akka.system.scheduler.scheduleOnce(ExpireOutdatedInterval.seconds)(_expireOutdated)
      )}
    }

    def _discardExpired: Future[Unit] = {
      discardExpiredPaymentRequests.map { _ => cancellableDiscardExpired = Some(
        Akka.system.scheduler.scheduleOnce(DiscardExpiredInterval.hours)(_discardExpired)
      )}
    }

    startPayGateway
    cancellableRunProcessable = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_runProcessable))
    cancellableExpireOutdated = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_expireOutdated))
    cancellableDiscardExpired = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_discardExpired))

    Logger.info("PayPlugin started")
  }

  /**
    * Called when the application stops.
    */
  override def onStop = {
    cancellableRunProcessable.foreach(_.cancel)
    cancellableExpireOutdated.foreach(_.cancel)
    stopPayGateway

    Logger.info("PayPlugin stopped")
  }
}

object PayPlugin {

  import scala.util.control.NonFatal

  /**
    * Starts the pay gateway.
    */
  def startPayGateway: Unit = {
    try {
      PayGateway.start
    } catch { case NonFatal(e) =>
      Logger.error("error starting pay gateway", e)
    }
  }

  /**
    * Stops the pay gateway.
    */
  def stopPayGateway: Unit = {
    try {
      PayGateway.stop
    } catch { case NonFatal(e) =>
      Logger.error("error stopping pay gateway", e)
    }
  }

  /**
    * Processes pending trade orders.
    */
  def processTradeOrders: Future[Unit] = {
    PayGateway.processTradeOrders.map { n =>
      if (n > 0) Logger.info(s"""processed $n trade order${if (n > 1) "s" else ""}""")
    }.recover { case NonFatal(e) =>
      Logger.error("error processing trade orders", e)
    }
  }

  /**
    * Expires payment requests stick around for too long.
    */
  def expirePaymentRequests: Future[Unit] = {
    PayGateway.expirePaymentRequests.map { n =>
      if (n > 0) Logger.info(s"""expired $n payment request${if (n > 1) "s" else ""}""")
    }.recover { case NonFatal(e) =>
      Logger.error("error expiring payment requests", e)
    }
  }

  /**
    * Discards payment requests expired for too long.
    */
  def discardExpiredPaymentRequests: Future[Unit] = {
    PayGateway.discardExpiredPaymentRequests.map { n =>
      if (n > 0) Logger.info(s"""discarded $n expired payment request${if (n > 1) "s" else ""}""")
    }.recover { case NonFatal(e) =>
      Logger.error("error discarding expired payment requests", e)
    }
  }
}
