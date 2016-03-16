/*#
  * @file PayErrors.scala
  * @begin 27-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import services.common.{BaseException, ErrorStackBase, WrappedLogger}

trait PayErrors extends ErrorStackBase {

  /** Converts pay errors to HTTP `Result`. */
  override def toResult = PayErrors.resolver orElse super.toResult
}

object PayErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class ActionFailed(action: String, cryptocurrency: String, coinAddress: String, reason: String) extends BaseException(
    s"$action $cryptocurrency to address $coinAddress failed: $reason"
  )
  final case class ActionNotAllowed(action: String, cryptocurrency: String, coinAddress: String, reason: String) extends BaseException(
    s"$action $cryptocurrency to address $coinAddress not allowed: $reason"
  )
  final case class AmountTooLow(`type`: String, currency: String, actualAmount: Double, minAmount: Double) extends BaseException(
    s"actual ${`type`} amount is ${currency} ${actualAmount} whereas it should be at least ${currency} ${minAmount}"
  )
  final case class CouldNotCreateInvoice(reason: String) extends BaseException(
    s"could not create invoice: $reason"
  )
  final case class CouldNotEnablePayout(objectId: String, coinAddress: String, reason: String) extends BaseException(
    s"error enabling payout to address $coinAddress for object $objectId: $reason"
  )
  final case class InvalidCoinAddress(coinAddress: String) extends BaseException(
    s"$coinAddress is an invalid coin address"
  )
  final case class NoRates(currencyPair: String, reason: String) extends BaseException(
    s"could not retrieve exchange rates for $currencyPair: $reason"
  )
  final case class OrphanedTransaction(coinAddress: String) extends BaseException(
    s"no order found that matches transaction associated with address $coinAddress"
  )
  final case class RequoteNotAllowed(orderId: String, reason: String) extends BaseException(
    s"requote of payment order $orderId not allowed: $reason"
  )
  final case class TradeFailed(`type`: String, cryptocurrency: String, reason: String) extends BaseException(
    s"${`type`} of $cryptocurrency failed: $reason"
  )
  final case class TradeNotAllowed(`type`: String, currency: String, reason: String) extends BaseException(
    s"${`type`} of $currency not allowed: $reason"
  )
  final case class XchangeError(url: String, response: String) extends BaseException(
    s"error invoking exchange endpoint $url: $response"
  )

  /**
    * Gets a `Resolver` that converts pay errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(
      _: AmountTooLow |
      _: InvalidCoinAddress |
      _: RequoteNotAllowed
    ), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
  }
}
