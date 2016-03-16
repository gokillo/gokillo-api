/*#
  * @file NewPayment.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator

/**
  * Represents `new_payment` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[NewPayment]] class.
  * @param json   The `new_payment` data as JSON.
  */
class NewPayment protected(json: JsValue) extends DefaultResponse(json) {

  def address = json as (__ \ 'data \ 'address).readNullable[String]
}

/**
  * Factory class for creating [[NewPayment]] instances.
  */
object NewPayment extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[NewPayment]] class with the specified JSON.
    *
    * @param json The `new_payment` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[NewPayment] = {
    validateNewPayment.reads(json).fold(
      valid = { validated => JsSuccess(new NewPayment(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[NewPayment]] to/from JSON.
    */
  implicit val newPaymentFormat = new Format[NewPayment] {
    def reads(json: JsValue) = NewPayment(json)
    def writes(newPayment: NewPayment) = newPayment.json
  }

  /**
    * Validates the JSON representation of a [[NewPayment]].
    * @return A `Reads` that validates the JSON representation of a [[NewPayment]].
    */
  private val validateNewPayment = (
    ((__ \ 'status).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'data \ 'address).json.pickBranch(Reads.of[JsString] <~ coinAddress) orEmpty) ~
    ((__ \ 'message).json.pickBranch orEmpty)
  ).reduce
}
