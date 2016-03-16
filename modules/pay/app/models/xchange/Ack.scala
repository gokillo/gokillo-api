/*#
  * @file Ack.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents a transaction ack coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[Ack]] class.
  * @param json   The ack data as JSON.
  */
class Ack protected(protected var json: JsValue) extends JsModel with api.Ack {

  def hash = json as (__ \ 'hash).read[String]
  def transactionType = json as (__ \ 'data \ 'type).read[String]
  def address = json as (__ \ 'data \ 'address).read[String]
  def amount = json as (__ \ 'data \ 'amount).read[Double]
  def currency = json as (__ \ 'data \ 'currency).read[String]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[Ack]] instances.
  */
object Ack extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  private val AckTypes = Seq("received", "sent")

  /**
    * Initializes a new instance of the [[Ack]] class with the specified JSON.
    *
    * @param json The ack data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Ack] = {
    validateAck.reads(json).fold(
      valid = { validated => JsSuccess(new Ack(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes an [[Ack]] to/from JSON.
    */
  implicit val ackFormat = new Format[Ack] {
    def reads(json: JsValue) = Ack(json)
    def writes(ack: Ack) = ack.json
  }

  /**
    * Validates the JSON representation of an [[Ack]].
    * @return A `Reads` that validates the JSON representation of an [[Ack]].
    */
  def validateAck = (
    ((__ \ 'hash).json.pickBranch) ~
    ((__ \ 'data \ 'address).json.pickBranch(Reads.of[JsString] <~ coinAddress)) ~
    ((__ \ 'data \ 'currency).json.pickBranch(Reads.of[JsString] <~ models.pay.Coin.currency)) ~ (
    ((__ \ 'data \ 'type).json.update(toTransactionType)) andThen
    ((__ \ 'data \ 'amount).json.update(toNumber))
  )).reduce

  /**
    * Validates the transaction type an maps it to an internal one.
    */
  private def toTransactionType(implicit reads: Reads[String]) = {
    import play.api.data.validation.ValidationError
    import models.pay.TransactionType._

    Reads[JsString](js => reads.reads(js).flatMap {
      case "received" => JsSuccess(JsString(Deposit.toString))
      case "sent" => JsSuccess(JsString(Withdrawal.toString))
      case value => JsError(ValidationError("error.transactionType", value))
    })
  }
}
