/*#
  * @file EurUsdRate.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator

/**
  * Represents `rates/eur_usd` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[EurUsdRate]] class.
  * @param json   The `rates/eur_usd` data as JSON.
  */
class EurUsdRate protected(json: JsValue) extends DefaultResponse(json) {

  def value = json as (__ \ Symbol("EUR-USD")).readNullable[Double]
}

/**
  * Factory class for creating [[EurUsdRate]] instances.
  */
object EurUsdRate extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[EurUsdRate]] class with the specified JSON.
    *
    * @param json The `rates/eur_usd` rate data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[EurUsdRate] = {
    validateEurUsdRate.reads(json).fold(
      valid = { validated => JsSuccess(new EurUsdRate(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[EurUsdRate]] to/from JSON.
    */
  implicit val tradeFormat = new Format[EurUsdRate] {
    def reads(json: JsValue) = EurUsdRate(json)
    def writes(eurUsdRate: EurUsdRate) = eurUsdRate.json
  }

  /**
    * Validates the JSON representation of a [[EurUsdRate]].
    * @return A `Reads` that validates the JSON representation of a [[EurUsdRate]].
    */
  private val validateEurUsdRate = (
    ((__ \ 'status).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'message).json.pickBranch orEmpty) ~
    ((__ \ Symbol("EUR-USD")).json.update(toNumber) orEmpty)
  ).reduce
}
