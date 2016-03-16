/*#
  * @file OrderStatus.scala
  * @begin 8-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines order statuses.
  */
object OrderStatus extends Enumeration {

  type OrderStatus = Value

  /**
    * Defines a new `Order` not yet processed.
    */
  val New = Value("new")

  /**
    * Defines a pending `Order`.
    */
  val Pending = Value("pending")

  /**
    * Defines a processed `Order`.
    */
  val Processed = Value("processed")

  /**
    * Defines a failed `Order`.
    */
  val Failed = Value("failed")

  /**
    * Defines an expired `Order`.
    */
  val Expired = Value("expired")

  /**
    * Serializes/Deserializes an [[OrderStatus]] to/from JSON.
    */
  implicit val orderStatusFormat = new Format[OrderStatus] {
    def reads(json: JsValue) = JsSuccess(OrderStatus(json.as[JsString].value))
    def writes(status: OrderStatus) = JsString(status.toString)
  }

  /**
    * Implicitly converts the specified [[OrderStatus]] to a string.
    *
    * @param orderStatus  The status to convert.
    * @return             The string converted from `orderStatus`.
    */
  implicit def toString(orderStatus: OrderStatus) = orderStatus.toString

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): OrderStatus = {
    try {
      return OrderStatus.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("order status", name)
    }
  }
}
