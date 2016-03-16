/*#
  * @file Transaction.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents `transaction` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[Transaction]] class.
  * @param json   The `transaction` data as JSON.
  */
class Transaction protected(protected var json: JsValue) extends JsModel {

  def id = json as (__ \ 'id).read[String]
  def orderId = json as (__ \ 'order_id).read[String]
  def microtime = json as (__ \ 'microtime).read[Double]
  def orderType = json as (__ \ 'type).read[String]
  def price = json as (__ \ 'price).read[Double]
  def amount = json as (__ \ 'amount).read[Double]
  def subtotal = json as (__ \ 'subtotal).read[Double]
  def fee = json as (__ \ 'fee).read[Double]
  def feeUsd = json as (__ \ 'feeUSD).read[Double]
  def total = json as (__ \ 'total).read[Double]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[Transaction]] instances.
  */
object Transaction extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Transaction]] class with the specified JSON.
    *
    * @param json The `transaction` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Transaction] = {
    validateTransaction.reads(json).fold(
      valid = { validated => JsSuccess(new Transaction(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[Transaction]] to/from JSON.
    */
  implicit val transactionFormat = new Format[Transaction] {
    def reads(json: JsValue) = Transaction(json)
    def writes(transaction: Transaction) = transaction.json
  }

  /**
    * Validates the JSON representation of a [[Transaction]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Transaction]].
    */
  def validateTransaction = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'order_id).json.pickBranch orEmpty) ~
    ((__ \ 'type).json.pickBranch orEmpty) ~ (
    ((__ \ 'microtime).json.update(toNumber)) andThen
    ((__ \ 'price).json.update(toNumber)) andThen
    ((__ \ 'amount).json.update(toNumber)) andThen
    ((__ \ 'subtotal).json.update(toNumber)) andThen
    ((__ \ 'fee).json.update(toNumber)) andThen
    ((__ \ 'feeUSD).json.update(toNumber)) andThen
    ((__ \ 'total).json.update(toNumber))
  )).reduce
}
