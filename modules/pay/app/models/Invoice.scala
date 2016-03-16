/*#
  * @file Invoice.scala
  * @begin 11-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._
import models.common.JsModel
import Coin._

/**
  * Represents an invoice to request a payment in cryptocurrency.
  *
  * @constructor  Initializes a new instance of the [[Invoice]] class.
  * @param json   The invoice as JSON.
  */
class Invoice protected(protected var json: JsValue) extends JsModel with api.Invoice {

  def orderId = json as (__ \ 'orderId).read[String]
  def orderId_= (v: String) = setValue((__ \ 'orderId), Json.toJson(v))
  def coinAddress = json as (__ \ 'coinAddress).read[String]
  def coinAddress_= (v: String) = setValue((__ \ 'coinAddress), Json.toJson(v))
  def amount = json as (__ \ 'amount).read[Coin]
  def amount_= (v: Coin) = setValue((__ \ 'amount), Json.toJson(v))
  def label = json as (__ \ 'label).readNullable[String]
  def label_= (v: Option[String]) = setValue((__ \ 'label), Json.toJson(v))
  def message = json as (__ \ 'message).readNullable[String]
  def message_= (v: Option[String]) = setValue((__ \ 'message), Json.toJson(v))
  def qrCode = json as (__ \ 'qrCode).read[String]
  def qrCode_= (v: String) = setValue((__ \ 'qrCode), Json.toJson(v))
  def expirationTime = json as (__ \ 'expirationTime).read[DateTime]
  def expirationTime_= (v: DateTime) = setValue((__ \ 'expirationTime), Json.toJson(v))

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[Invoice]] instances.
  */
object Invoice {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[Invoice]] class with the specified values.
    *
    * @param orderId        The identifier of the order this invoice is associated with.
    * @param coinAddress    The receiving coin address.
    * @param amount         The amount to be paid.
    * @param label          The label associated with `coinAddress`.
    * @param message        The message that describes the transaction.
    * @param qrCode         The invoice QR-Code.
    * @param expirationTime The time the invoice expires.
    * @return               A new instance of the [[Invoice]] class.
    */
  def apply(
    orderId: String,
    coinAddress: String,
    amount: Coin,
    label: Option[String],
    message: Option[String],
    qrCode: String,
    expirationTime: DateTime
  ): Invoice = new Invoice(
    invoiceWrites.writes(
      orderId,
      coinAddress,
      amount,
      label,
      message,
      qrCode,
      expirationTime
    )
  ) 

  /**
    * Extracts the content of the specified [[Invoice]].
    *
    * @param invoice  The [[Invoice]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `invoice` is `null`.
    */
  def unapply(invoice: Invoice) = {
    if (invoice eq null) None
    else Some((
      invoice.orderId,
      invoice.coinAddress,
      invoice.amount,
      invoice.label,
      invoice.message,
      invoice.qrCode,
      invoice.expirationTime
    ))
  }

  /**
    * Serializes an [[Invoice]] to JSON.
    * @note Used internally by `apply`.
    */
  private val invoiceWrites = (
    (__ \ 'orderId).write[String] ~
    (__ \ 'coinAddress).write[String] ~
    (__ \ 'amount).write[Coin] ~
    (__ \ 'label).writeNullable[String] ~
    (__ \ 'message).writeNullable[String] ~
    (__ \ 'qrCode).write[String] ~
    (__ \ 'expirationTime).write[DateTime]
  ).tupled
}
