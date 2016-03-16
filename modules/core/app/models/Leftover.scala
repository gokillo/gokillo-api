/*#
  * @file Leftover.scala
  * @begin 6-Oct-2015
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
  * Represents monetary value left due to roundings or exchange variations.
  *
  * @constructor  Initializes a new instance of the [[Leftover]] class.
  * @param json   Tha leftover information as JSON.
  */
class Leftover protected(protected var json: JsValue) extends JsEntity with api.Leftover {

  def amount = json as (__ \ 'amount).read[Coin]
  def amount_= (v: Coin) = setValue((__ \ 'amount), Json.toJson(v))
  def count = json as (__ \ 'count).read[Int]
  def count_= (v: Int) = setValue((__ \ 'count), Json.toJson(v))
  def withdrawalTime = json as (__ \ 'withdrawalTime).readNullable[DateTime]
  def withdrawalTime_= (v: Option[DateTime]) = setValue((__ \ 'withdrawalTime), Json.toJson(v))

  def copy(leftover: Leftover): Leftover = new Leftover(this.json.as[JsObject] ++ leftover.json.as[JsObject])
  def copy(json: JsValue): Leftover = Leftover(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(leftover, _) => leftover
    case JsError(_) => new Leftover(this.json)
  }
}

/**
  * Factory class for creating [[Leftover]] instances.
  */
object Leftover {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Leftover]] class with the specified JSON.
    *
    * @param json The leftover information as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Leftover] = {
    validateLeftover.reads(json).fold(
      valid = { validated => JsSuccess(new Leftover(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Leftover]] class with the specified values.
    *
    * @param id             The identifier of the leftover.
    * @param amount         The amount of the leftover.
    * @param count          The number of times the amount has been increased.
    * @param withdrawalTime The time the leftover was withdrawn.
    * @param creationTime   The time the leftover was created.
    * @return               A new instance of the [[Leftover]] class.
    */
  def apply(
    id: Option[String] = None,
    amount: Coin,
    count: Int,
    withdrawalTime: Option[DateTime] = None,
    creationTime: Option[DateTime] = None
  ): Leftover = new Leftover(
    leftoverWrites.writes(
      id,
      amount,
      count,
      withdrawalTime,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Leftover]].
    *
    * @param leftover The [[Leftover]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `leftover` is `null`.
    */
  def unapply(leftover: Leftover) = {
    if (leftover eq null) None
    else Some((
      leftover.id,
      leftover.amount,
      leftover.count,
      leftover.withdrawalTime,
      leftover.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Leftover]] to/from JSON.
    */
  implicit val leftoverFormat = new Format[Leftover] {
    def reads(json: JsValue) = Leftover(json)
    def writes(leftover: Leftover) = leftover.json
  }

  /**
    * Serializes a [[Leftover]] to JSON.
    * @note Used internally by `apply`.
    */
  private val leftoverWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'amount).write[Coin] ~
    (__ \ 'count).write[Int] ~
    (__ \ 'withdrawalTime).writeNullable[DateTime] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of a monetary value.
    */
  private def coin(field: Symbol) = (__ \ field).json.pick[JsValue] andThen validateCoin

  /**
    * Validates the JSON representation of a [[Leftover]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Leftover]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateLeftover = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'amount).json.copyFrom(coin(Symbol("amount")))) ~
    ((__ \ 'count).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'withdrawalTime).json.pickBranch orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce
}
