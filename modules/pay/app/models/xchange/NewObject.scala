/*#
  * @file NewObject.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator

/**
  * Represents `new_object` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[NewObject]] class.
  * @param json   The `new_object` data as JSON.
  */
class NewObject protected(json: JsValue) extends DefaultResponse(json) {

  def id = json as (__ \ 'data \ 'id).read[String]
  def address = json as (__ \ 'data \ 'address).readNullable[String]
  def currency = json as (__ \ 'data \ 'currency).readNullable[String]
}

/**
  * Factory class for creating [[NewObject]] instances.
  */
object NewObject extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[NewObject]] class with the specified JSON.
    *
    * @param json The `new_object` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[NewObject] = {
    validateNewObject.reads(json).fold(
      valid = { validated => JsSuccess(new NewObject(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[NewObject]] to/from JSON.
    */
  implicit val newObjectFormat = new Format[NewObject] {
    def reads(json: JsValue) = NewObject(json)
    def writes(newObject: NewObject) = newObject.json
  }

  /**
    * Validates the JSON representation of a [[NewObject]].
    * @return A `Reads` that validates the JSON representation of a [[NewObject]].
    */
  private val validateNewObject = (
    ((__ \ 'status).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'data \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'data \ 'address).json.pickBranch(Reads.of[JsString] <~ coinAddress) orEmpty) ~
    ((__ \ 'data \ 'currency).json.pickBranch(Reads.of[JsString] <~ models.pay.Coin.currency) orEmpty) ~
    ((__ \ 'message).json.pickBranch orEmpty)
  ).reduce
}
