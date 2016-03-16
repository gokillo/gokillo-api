/*#
  * @file OrderType.scala
  * @begin 8-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines order types.
  */
object OrderType extends Enumeration {

  type OrderType = Value

  /**
    * Defines a buy order.
    */
  val Buy = Value("buy")

  /**
    * Defines a sell order.
    */
  val Sell = Value("sell")

  /**
    * Defines a payment order.
    */
  val Payment = Value("payment")

  /**
    * Defines a payment request order.
    */
  val PaymentRequest = Value("paymentRequest")

  /**
    * Defines an order to transfer coins from Exchange to local wallet.
    */
  val Transfer = Value("transfer")

  /**
    * Defines an order to send coins from local wallet to any recipient.
    */
  val Send = Value("send")

  /**
    * Serializes/Deserializes an [[OrderType]] to/from JSON.
    */
  implicit val orderTypeFormat = new Format[OrderType] {
    def reads(json: JsValue) = JsSuccess(OrderType(json.as[JsString].value))
    def writes(orderType: OrderType) = JsString(orderType.toString)
  }

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): OrderType = {
    try {
      return OrderType.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("order type", name)
    }
  }
}
