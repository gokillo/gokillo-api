/*#
  * @file RateType.scala
  * @begin 19-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines rate types.
  */
object RateType extends Enumeration {

  type RateType = Value

  /**
    * Defines a rate of type ''ask''.
    */
  val Ask = Value("ask")

  /**
    * Defines a rate of type ''bid''.
    */
  val Bid = Value("bid")

  /**
    * Defines a rate of type ''last''.
    */
  val Last = Value("last")

  /**
    * Defines a rate of type ''high''.
    */
  val High = Value("high")

  /**
    * Defines a rate of type ''low''.
    */
  val Low = Value("low")

  /**
    * Serializes/Deserializes a [[RateType]] to/from JSON.
    */
  implicit val rateTypeFormat = new Format[RateType] {
    def reads(json: JsValue) = JsSuccess(RateType(json.as[JsString].value))
    def writes(rateType: RateType) = JsString(rateType.toString)
  }

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): RateType = {
    try {
      return RateType.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("rate type", name)
    }
  }
}
