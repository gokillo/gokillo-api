/*#
  * @file CoinAddressChange.scala
  * @begin 28-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents a coin address change.
  *
  * @constructor  Initializes a new instance of the [[CoinAddressChange]] class.
  * @param json   The coin address change data as JSON.
  */
class CoinAddressChange protected(
  protected var json: JsValue
) extends JsModel with api.CoinAddressChange {

  def currentCoinAddress = json as (__ \ 'currentCoinAddress).read[String]
  def currentCoinAddress_= (v: String) = setValue((__ \ 'currentCoinAddress), Json.toJson(v))
  def newCoinAddress = json as (__ \ 'newCoinAddress).read[String]
  def newCoinAddress_= (v: String) = setValue((__ \ 'newCoinAddress), Json.toJson(v))

  def copy(coinAddressChange: CoinAddressChange): CoinAddressChange = new CoinAddressChange(this.json.as[JsObject] ++ coinAddressChange.json.as[JsObject])
  def copy(json: JsValue): CoinAddressChange = CoinAddressChange(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(coinAddressChange, _) => coinAddressChange
    case JsError(_) => new CoinAddressChange(this.json)
  }
}

/**
  * Factory class for creating [[CoinAddressChange]] instances.
  */
object CoinAddressChange extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[CoinAddressChange]] class with the specified JSON.
    *
    * @param json The coin address change data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[CoinAddressChange] = {
    validateCoinAddressChange.reads(json).fold(
      valid = { validated => JsSuccess(new CoinAddressChange(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[CoinAddressChange]] class with the specified values.
    *
    * @param currentCoinAddress The current coin address.
    * @param newCoinAddress     The new coin address to set.
    * @return                   A new instance of the [[CoinAddressChange]] class.
    */
  def apply(
    currentCoinAddress: String,
    newCoinAddress: String
  ): CoinAddressChange = new CoinAddressChange(coinAddressChangeWrites.writes(
    currentCoinAddress,
    newCoinAddress
  ))

  /**
    * Extracts the content of the specified [[CoinAddressChange]].
    *
    * @param coinAddressChange  The [[CoinAddressChange]] to extract the content from.
    * @return                   An `Option` that contains the extracted data,
    *                           or `None` if `coinAddressChange` is `null`.
    */
  def unapply(coinAddressChange: CoinAddressChange) = {
    if (coinAddressChange eq null) None
    else Some((
      coinAddressChange.currentCoinAddress,
      coinAddressChange.newCoinAddress
    ))
  }

  /**
    * Serializes/Deserializes a [[CoinAddressChange]] to/from JSON.
    */
  implicit val coinAddressChangeFormat: Format[CoinAddressChange] = new Format[CoinAddressChange] {
    def reads(json: JsValue) = CoinAddressChange(json)
    def writes(coinAddressChange: CoinAddressChange) = coinAddressChange.json
  }

  /**
    * Serializes a [[CoinAddressChange]] to JSON.
    * @note Used internally by `apply`.
    */
  private val coinAddressChangeWrites = (
    (__ \ 'currentCoinAddress).write[String] ~
    (__ \ 'newCoinAddress).write[String]
  ).tupled

  /**
    * Validates the JSON representation of a [[CoinAddressChange]].
    *
    * @return A `Reads` that validates the JSON representation of a [[CoinAddressChange]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateCoinAddressChange = (
    ((__ \ 'currentCoinAddress).json.pickBranch(Reads.of[JsString] <~ coinAddress)) ~
    ((__ \ 'newCoinAddress).json.pickBranch(Reads.of[JsString] <~ coinAddress))
  ).reduce
}
