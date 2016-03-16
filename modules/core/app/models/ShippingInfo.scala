/*#
  * @file ShippingInfo.scala
  * @begin 22-Dec-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import play.api.libs.json._
import models.common.{Address, JsModel}
import models.common.Address._

/**
  * Provides shipping info for the delivery of physical rewards.
  *
  * @constructor  Initializes a new instance of the [[ShippingInfo]] class.
  * @param json   The shipping info data as JSON.
  */
class ShippingInfo protected(
  protected var json: JsValue
) extends JsModel with api.ShippingInfo {

  def address = json as (__ \ 'address).read[Address]
  def address_= (v: Address) = setValue((__ \ 'address), Json.toJson(v))
  def notice = json as (__ \ 'notice).readNullable[String]
  def notice_= (v: Option[String]) = setValue((__ \ 'notice), Json.toJson(v))

  def copy(shippingInfo: ShippingInfo): ShippingInfo = new ShippingInfo(this.json.as[JsObject] ++ shippingInfo.json.as[JsObject])
  def copy(json: JsValue): ShippingInfo = ShippingInfo(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(shippingInfo, _) => shippingInfo
    case JsError(_) => new ShippingInfo(this.json)
  }
}

/**
  * Factory class for creating [[ShippingInfo]] instances.
  */
object ShippingInfo {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[ShippingInfo]] class with the specified JSON.
    *
    * @param json The shipping info data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[ShippingInfo] = {
    validateShippingInfo.reads(json).fold(
      valid = { validated => JsSuccess(new ShippingInfo(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[ShippingInfo]] class with the specified values.
    *
    * @param address  The address where physical rewards are received.
    * @param notice   Any notice regarding the delivery.
    * @return         A new instance of the [[ShippingInfo]] class.
    */
  def apply(
    address: Address,
    notice: Option[String] = None
  ): ShippingInfo = new ShippingInfo(shippingInfoWrites.writes(
    address,
    notice
  ))

  /**
    * Extracts the content of the specified [[ShippingInfo]].
    *
    * @param shippingInfo The [[ShippingInfo]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `shippingInfo` is `null`.
    */
  def unapply(shippingInfo: ShippingInfo) = {
    if (shippingInfo eq null) None
    else Some((
      shippingInfo.address,
      shippingInfo.notice
    ))
  }

  /**
    * Serializes/Deserializes a [[ShippingInfo]] to/from JSON.
    */
  implicit val shippingInfoFormat: Format[ShippingInfo] = new Format[ShippingInfo] {
    def reads(json: JsValue) = ShippingInfo(json)
    def writes(shippingInfo: ShippingInfo) = shippingInfo.json
  }

  /**
    * Serializes a [[ShippingInfo]] to JSON.
    * @note Used internally by `apply`.
    */
  private val shippingInfoWrites = (
    (__ \ 'address).write[Address] ~
    (__ \ 'notice).writeNullable[String]
  ).tupled

  /**
    * Validates the JSON representation of the specified address.
    */
  private val address = (__ \ 'address).json.pick[JsValue] andThen validateAddress(false)

  /**
    * Validates the JSON representation of a [[ShippingInfo]].
    *
    * @return A `Reads` that validates the JSON representation of a [[ShippingInfo]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateShippingInfo = (
    ((__ \ 'address).json.copyFrom(address)) ~
    ((__ \ 'notice).json.pickBranch orEmpty)
  ).reduce
}
