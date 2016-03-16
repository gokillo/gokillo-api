/*#
  * @file Trade.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._

/**
  * Represents `trade` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[Trade]] class.
  * @param json   The `trade` data as JSON.
  */
class Trade protected(json: JsValue) extends DefaultResponse(json) {

  def orderId = json as (__ \ 'order_id).readNullable[Int]
}

/**
  * Factory class for creating [[Trade]] instances.
  */
object Trade {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Trade]] class with the specified JSON.
    *
    * @param json The `trade` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Trade] = {
    validateTrade.reads(json).fold(
      valid = { validated => JsSuccess(new Trade(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[Trade]] to/from JSON.
    */
  implicit val tradeFormat = new Format[Trade] {
    def reads(json: JsValue) = Trade(json)
    def writes(trade: Trade) = trade.json
  }

  /**
    * Validates the JSON representation of a [[Trade]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Trade]].
    */
  private val validateTrade = (
    ((__ \ 'status).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'order_id).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'message).json.pickBranch orEmpty)
  ).reduce
}
