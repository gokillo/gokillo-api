/*#
  * @file Fee.scala
  * @begin 30-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._
import models.common.JsEntity
import models.pay.Coin
import models.pay.Coin._

/**
  * Provides information about the fee withheld from the amount raised
  * by a project on success.
  *
  * @constructor  Initializes a new instance of the [[Fee]] class.
  * @param json   Tha fee information as JSON.
  */
class Fee protected(protected var json: JsValue) extends JsEntity with api.Fee {

  def projectId = json as (__ \ 'id).read[String]
  def projectId_= (v: String) = setValue((__ \ 'id), Json.toJson(v))
  def amount = json as (__ \ 'amount).read[Coin]
  def amount_= (v: Coin) = setValue((__ \ 'amount), Json.toJson(v))
  def vat = json as (__ \ 'vat).readNullable[Coin]
  def vat_= (v: Option[Coin]) = setValue((__ \ 'vat), Json.toJson(v))
  def withdrawalTime = json as (__ \ 'withdrawalTime).readNullable[DateTime]
  def withdrawalTime_= (v: Option[DateTime]) = setValue((__ \ 'withdrawalTime), Json.toJson(v))

  def copy(fee: Fee): Fee = new Fee(this.json.as[JsObject] ++ fee.json.as[JsObject])
  def copy(json: JsValue): Fee = Fee(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(fee, _) => fee
    case JsError(_) => new Fee(this.json)
  }
}

/**
  * Factory class for creating [[Fee]] instances.
  */
object Fee {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Fee]] class with the specified JSON.
    *
    * @param json The fee information as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Fee] = {
    validateFee.reads(json).fold(
      valid = { validated => JsSuccess(new Fee(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Fee]] class with the specified values.
    *
    * @param id             The identifier of the fee.
    * @param projectId      The identifier of the project the fee applied to.
    * @param amount         The amount of the fee.
    * @param vat            The amount of the value-added tax, if applicable.
    * @param withdrawalTime The time the fee was withdrawn.
    * @param creationTime   The time the fee was created.
    * @return               A new instance of the [[Fee]] class.
    */
  def apply(
    id: Option[String] = None,
    projectId: String,
    amount: Coin,
    vat: Option[Coin] = None,
    withdrawalTime: Option[DateTime] = None,
    creationTime: Option[DateTime] = None
  ): Fee = new Fee(
    feeWrites.writes(
      id,
      projectId,
      amount,
      vat,
      withdrawalTime,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Fee]].
    *
    * @param fee  The [[Fee]] to extract the content from.
    * @return     An `Option` that contains the extracted data,
    *             or `None` if `fee` is `null`.
    */
  def unapply(fee: Fee) = {
    if (fee eq null) None
    else Some((
      fee.id,
      fee.projectId,
      fee.amount,
      fee.vat,
      fee.withdrawalTime,
      fee.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Fee]] to/from JSON.
    */
  implicit val feeFormat = new Format[Fee] {
    def reads(json: JsValue) = Fee(json)
    def writes(fee: Fee) = fee.json
  }

  /**
    * Serializes a [[Fee]] to JSON.
    * @note Used internally by `apply`.
    */
  private val feeWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'projectId).write[String] ~
    (__ \ 'amount).write[Coin] ~
    (__ \ 'vat).writeNullable[Coin] ~
    (__ \ 'withdrawalTime).writeNullable[DateTime] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of a monetary value.
    */
  private def coin(field: Symbol) = (__ \ field).json.pick[JsValue] andThen validateCoin

  /**
    * Validates the JSON representation of a [[Fee]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Fee]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateFee = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'projectId).json.pickBranch) ~
    ((__ \ 'amount).json.copyFrom(coin(Symbol("amount")))) ~
    ((__ \ 'vat).json.copyFrom(coin(Symbol("vat"))) orEmpty) ~
    ((__ \ 'withdrawalTime).json.pickBranch orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce
}
