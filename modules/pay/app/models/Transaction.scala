/*#
  * @file Transaction.scala
  * @begin 28-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsEntity
import TransactionType._
import Coin._

/**
  * Represents a pay transaction.
  *
  * @constructor  Initializes a new instance of the [[Transaction]] class.
  * @param json   The transaction data as JSON.
  */
class Transaction protected(protected var json: JsValue) extends JsEntity with api.Transaction {

  def orderId = json as (__ \ 'orderId).readNullable[String]
  def orderId_= (v: Option[String]) = setValue((__ \ 'orderId), Json.toJson(v))
  def transactionType = json as (__ \ 'type).read[TransactionType]
  def transactionType_= (v: TransactionType) = setValue((__ \ 'type), Json.toJson(v))
  def hash = json as (__ \ 'hash).readNullable[String]
  def hash_= (v: Option[String]) = setValue((__ \ 'hash), Json.toJson(v))
  def coinAddress = json as (__ \ 'coinAddress).readNullable[String]
  def coinAddress_= (v: Option[String]) = setValue((__ \ 'coinAddress), Json.toJson(v))
  def amount = json as (__ \ 'amount).read[Coin]
  def amount_= (v: Coin) = setValue((__ \ 'amount), Json.toJson(v))
  def fee = json as (__ \ 'fee).readNullable[Coin]
  def fee_= (v: Option[Coin]) = setValue((__ \ 'fee), Json.toJson(v))

  def copy(json: JsValue): Transaction = Transaction(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(transaction, _) => transaction
    case JsError(_) => new Transaction(this.json)
  }
}

/**
  * Factory class for creating [[Transaction]] instances.
  */
object Transaction extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Transaction]] class with the specified JSON.
    *
    * @param json The transaction data as JSON.
    * @return     A `JsResult` value containing the new class instance.
    */
  private def apply(json: JsValue): JsResult[Transaction] = JsSuccess(new Transaction(json))

  /**
    * Initializes a new instance of the [[Transaction]] class with the specified values.
    *
    * @param id           The identifier of the transaction.
    * @param orderId      The identifier of the order that generated the transaction.
    * @param transactionType One of the [[TransactionType]] values.
    * @param hash         The transaction hash.
    * @param coinAddress  The recipient coin address.
    * @param amount       The amount of the transaction.
    * @param fee          The transaction fee, if any.
    * @param creationTime The time the transaction was created.
    * @return             A new instance of the [[Transaction]] class.
    * @note               Attribute `fromCoinAddress` is omitted in case of withdrawal
    *                     or trading order.
    */
  def apply(
    id: Option[String],
    orderId: Option[String],
    transactionType: TransactionType,
    hash: Option[String],
    coinAddress: Option[String],
    amount: Coin,
    fee: Option[Coin],
    creationTime: Option[DateTime] = None
  ): Transaction = new Transaction(
    transactionWrites.writes(
      id,
      orderId,
      transactionType,
      hash,
      coinAddress,
      amount,
      fee,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Transaction]].
    *
    * @param transaction  The [[Transaction]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `transaction` is `null`.
    */
  def unapply(transaction: Transaction) = {
    if (transaction eq null) None
    else Some((
      transaction.id,
      transaction.orderId,
      transaction.transactionType,
      transaction.hash,
      transaction.coinAddress,
      transaction.amount,
      transaction.fee,
      transaction.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a pay transaction to/from JSON.
    */
  implicit val transactionFormat = new Format[Transaction] {
    def reads(json: JsValue) = Transaction(json)
    def writes(transaction: Transaction) = transaction.json
  }

  /**
    * Serializes a [[Transaction]] to JSON.
    * @note Used internally by `apply`.
    */
  private val transactionWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'orderId).writeNullable[String] ~
    (__ \ 'type).write[TransactionType] ~
    (__ \ 'hash).writeNullable[String] ~
    (__ \ 'coinAddress).writeNullable[String] ~
    (__ \ 'amount).write[Coin] ~
    (__ \ 'fee).writeNullable[Coin] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled
}
