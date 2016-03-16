/*#
  * @file PaymentRequest.scala
  * @begin 15-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._
import models.common.{JsModel, RefId}
import models.common.RefId._
import models.auth.IdentityMode._
import Coin._

/**
  * Represents a payment request in fiat amount.
  *
  * @constructor  Initializes a new instance of the [[PaymentRequest]] class.
  * @param json   The payment request data as JSON.
  */
class PaymentRequest protected(protected var json: JsValue) extends JsModel with api.PaymentRequest {

  def refId = json as (__ \ 'refId).read[RefId]
  def refId_= (v: RefId) = setValue((__ \ 'refId), Json.toJson(v))
  def amount = json as (__ \ 'amount).read[Coin]
  def amount_= (v: Coin) = setValue((__ \ 'amount), Json.toJson(v))
  def accountId = json as (__ \ 'accountId).readNullable[String]
  def accountId_= (v: Option[String]) = setValue((__ \ 'accountId), Json.toJson(v))
  def orderId = json as (__ \ 'orderId).readNullable[String]
  def orderId_= (v: Option[String]) = setValue((__ \ 'orderId), Json.toJson(v))
  def issuerIdentityMode = json as (__ \ 'issuerIdentityMode).readNullable[IdentityMode]
  def issuerIdentityMode_= (v: Option[IdentityMode]) = setValue((__ \ 'issuerIdentityMode), Json.toJson(v))
  def label = json as (__ \ 'label).readNullable[String]
  def label_= (v: Option[String]) = setValue((__ \ 'label), Json.toJson(v))
  def message = json as (__ \ 'message).readNullable[String]
  def message_= (v: Option[String]) = setValue((__ \ 'message), Json.toJson(v))

  def copy(paymentRequest: PaymentRequest): PaymentRequest = new PaymentRequest(this.json.as[JsObject] ++ paymentRequest.json.as[JsObject])
  def copy(json: JsValue): PaymentRequest = PaymentRequest(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(paymentRequest, _) => paymentRequest
    case JsError(_) => new PaymentRequest(this.json)
  }
}

/**
  * Factory class for creating [[PaymentRequest]] instances.
  */
object PaymentRequest {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[PaymentRequest]] class with the specified JSON.
    *
    * @param json The payment request data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[PaymentRequest] = {
    validatePaymentRequest.reads(json).fold(
      valid = { validated => JsSuccess(new PaymentRequest(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[PaymentRequest]] class with the specified values.
    *
    * @param refId      A reference to the object associated with the payment request.
    * @param amount     The amount to get paid.
    * @param accountId  The identifier of the account that receives the payment request.
    * @param orderId    The identifier of the order associated with the payment request.
    * @param issuerIdentityMode One of the [[models.auth.IdentityMode]] values.
    * @param label      The label associated with the payment request.
    * @param message    The message that describes the payment request.
    * @return           A new instance of the [[PaymentRequest]] class.
    * @note             If `orderId` is `None` it means the payment request is new and the invoice
    *                   has not been emitted yet, otherwise it means the invoice was already emitted
    *                   but is no longer valid and a new one needs to be emitted alongside a requote.
    */
  def apply(
    refId: RefId,
    amount: Coin,
    accountId: Option[String] = None,
    orderId: Option[String] = None,
    issuerIdentityMode: Option[IdentityMode] = None,
    label: Option[String] = None,
    message: Option[String] = None
  ): PaymentRequest = new PaymentRequest(
    paymentRequestWrites.writes(
      refId,
      amount,
      accountId,
      orderId,
      issuerIdentityMode,
      label,
      message
    )
  ) 

  /**
    * Extracts the content of the specified [[PaymentRequest]].
    *
    * @param paymentRequest The [[PaymentRequest]] to extract the content from.
    * @return               An `Option` that contains the extracted data,
    *                       or `None` if `paymentRequest` is `null`.
    */
  def unapply(paymentRequest: PaymentRequest) = {
    if (paymentRequest eq null) None
    else Some((
      paymentRequest.refId,
      paymentRequest.amount,
      paymentRequest.accountId,
      paymentRequest.orderId,
      paymentRequest.issuerIdentityMode,
      paymentRequest.label,
      paymentRequest.message
    ))
  }

  /**
    * Serializes/Deserializes a [[PaymentRequest]] to/from JSON.
    */
  implicit val paymentRequestFormat = new Format[PaymentRequest] {
    def reads(json: JsValue) = PaymentRequest(json)
    def writes(paymentRequest: PaymentRequest) = paymentRequest.json
  }

  /**
    * Serializes a [[PaymentRequest]] to JSON.
    * @note Used internally by `apply`.
    */
  private val paymentRequestWrites = (
    (__ \ 'refId).write[RefId] ~
    (__ \ 'amount).write[Coin] ~
    (__ \ 'accountId).writeNullable[String] ~
    (__ \ 'orderId).writeNullable[String] ~
    (__ \ 'issuerIdentityMode).writeNullable[IdentityMode] ~
    (__ \ 'label).writeNullable[String] ~
    (__ \ 'message).writeNullable[String]
  ).tupled

  /**
    * Validates the JSON representation of the reference id.
    */
  private val refId = (__ \ 'refId).json.pick[JsValue] andThen validateRefId

  /**
    * Validates the JSON representation of a monetary value.
    */
  private val amount = (__ \ 'amount).json.pick[JsValue] andThen validateCoin

  /**
    * Validates the JSON representation of a [[PaymentRequest]].
    *
    * @return A `Reads` that validates the JSON representation of a [[PaymentRequest]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  def validatePaymentRequest = (
    ((__ \ 'refId).json.copyFrom(refId)) ~
    ((__ \ 'amount).json.copyFrom(amount)) ~
    ((__ \ 'accountId).json.pickBranch orEmpty) ~
    ((__ \ 'orderId).json.pickBranch orEmpty) ~
    ((__ \ 'ownerIdentityMode).json.pickBranch(Reads.of[JsString] <~ identityMode) orEmpty) ~
    ((__ \ 'label).json.pickBranch orEmpty) ~
    ((__ \ 'message).json.pickBranch orEmpty)
  ).reduce
}
