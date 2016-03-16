/*#
  * @file CoinNet.scala
  * @begin 29-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines available coin networks.
  */
object CoinNet extends Enumeration {

  type CoinNet = Value

  /**
    * Defines the main production network on which people trade goods and services.
    */
  val Prod = Value("prod")

  /**
    * Defines the test network suitable for development.
    */
  val Test = Value("test")

  /**
    * Serializes/Deserializes a [[CoinNet]] to/from JSON.
    */
  implicit val coinNetFormat = new Format[CoinNet] {
    def reads(json: JsValue) = JsSuccess(CoinNet(json.as[JsString].value))
    def writes(coinNet: CoinNet) = JsString(coinNet.toString)
  }

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): CoinNet = {
    try {
      return CoinNet.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("coin network", name)
    }
  }
}
