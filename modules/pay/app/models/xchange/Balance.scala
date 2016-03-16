/*#
  * @file Balance.scala
  * @begin 22-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents `balance` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[Balance]] class.
  * @param json   The `balance` data as JSON.
  */
class Balance protected(protected var json: JsValue) extends JsModel {

  def availableUsd = json as (__ \ 'available_usd).read[Double]
  def availableCcu = json as (__ \ 'available_btc).read[Double]
  def reservedOrderUsd = json as (__ \ 'reserved_order_usd).read[Double]
  def reservedOrderCcu = json as (__ \ 'reserved_order_btc).read[Double]
  def reservedWithdrawUsd = json as (__ \ 'reserved_withdraw_usd).read[Double]
  def reservedWithdrawCcu = json as (__ \ 'reserved_withdraw_btc).read[Double]
  def fee = json as (__ \ 'fee).read[Double]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[Balance]] instances.
  */
object Balance extends Validator {

  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[Balance]] class with the specified JSON.
    *
    * @param json The `balance` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Balance] = {
    validateBalance.reads(json).fold(
      valid = { validated => JsSuccess(new Balance(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[Balance]] to/from JSON.
    */
  implicit val balanceFormat = new Format[Balance] {
    def reads(json: JsValue) = Balance(json)
    def writes(balance: Balance) = balance.json
  }

  /**
    * Validates the JSON representation of a [[Balance]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Balance]].
    */
  def validateBalance = (
    ((__ \ 'available_usd).json.update(toNumber)) andThen
    ((__ \ 'available_btc).json.update(toNumber)) andThen
    ((__ \ 'reserved_order_usd).json.update(toNumber)) andThen
    ((__ \ 'reserved_order_btc).json.update(toNumber)) andThen
    ((__ \ 'reserved_withdraw_usd).json.update(toNumber)) andThen
    ((__ \ 'reserved_withdraw_btc).json.update(toNumber)) andThen
    ((__ \ 'fee).json.update(toNumber))
  )
}
