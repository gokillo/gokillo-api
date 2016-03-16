/*#
  * @file OrderDetails.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents `order_details` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[OrderDetails]] class.
  * @param json   The `order_details` data as JSON.
  */
class OrderDetails protected(protected var json: JsValue) extends JsModel {

  def id = json as (__ \ 'id).read[String]
  def microtime = json as (__ \ 'microtime).read[Double]
  def orderType = json as (__ \ 'type).read[String]
  def price = json as (__ \ 'price).read[Double]
  def amount = json as (__ \ 'amount).read[Double]
  def initialAmount = json as (__ \ 'initial_amount).read[Double]
  def wavg = json as (__ \ 'wavg).read[Double]
  def fee = json as (__ \ 'fee).read[Double]
  def feeUsd = json as (__ \ 'feeUSD).read[Double]
  def initial_feeUsd = json as (__ \ 'initial_feeUSD).read[Double]
  def deleted = json as (__ \ 'deleted).read[Int]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[OrderDetails]] instances.
  */
object OrderDetails extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[OrderDetails]] class with the specified JSON.
    *
    * @param json The `order_details` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[OrderDetails] = {
    validateOrderDetails.reads(json).fold(
      valid = { validated => JsSuccess(new OrderDetails(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes an [[OrderDetails]] to/from JSON.
    */
  implicit val orderDetailsFormat = new Format[OrderDetails] {
    def reads(json: JsValue) = OrderDetails(json)
    def writes(orderDetails: OrderDetails) = orderDetails.json
  }

  /**
    * Validates the JSON representation of an [[OrderDetails]].
    *
    * @return A `Reads` that validates the JSON representation of an [[OrderDetails]].
    */
  def validateOrderDetails =  (
    ((__ \ 'id).json.pickBranch) ~
    ((__ \ 'type).json.pickBranch) ~ (
    ((__ \ 'microtime).json.update(toNumber)) andThen
    ((__ \ 'price).json.update(toNumber)) andThen
    ((__ \ 'amount).json.update(toNumber)) andThen
    ((__ \ 'initial_amount).json.update(toNumber)) andThen
    ((__ \ 'wavg).json.update(toNumber)) andThen
    ((__ \ 'fee).json.update(toNumber)) andThen
    ((__ \ 'feeUSD).json.update(toNumber)) andThen
    ((__ \ 'initial_feeUSD).json.update(toNumber)) andThen
    ((__ \ 'deleted).json.update(toNumber))
  )).reduce
}
