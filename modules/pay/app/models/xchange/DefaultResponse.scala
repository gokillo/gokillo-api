/*#
  * @file DefaultResponse.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import models.common.JsModel

/**
  * Represents a default response coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[DefaultResponse]] class.
  * @param json   The response data as JSON.
  */
class DefaultResponse protected(protected var json: JsValue) extends JsModel {

  def status = json as (__ \ 'status).read[Int]
  def message = json as (__ \ 'message).readNullable[String]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Base trait for any factory object.
  */
trait DefaultResponseBase {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Validates the JSON representation of a [[DefaultResponse]].
    * @return A `Reads` that validates the JSON representation of a [[DefaultResponse]].
    */
  protected val validateDefaultResponse = (
    ((__ \ 'status).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'message).json.pickBranch orEmpty)
  ).reduce
}

/**
  * Factory class for creating [[DefaultResponse]] instances.
  */
object DefaultResponse extends DefaultResponseBase {

  /**
    * Initializes a new instance of the [[DefaultResponse]] class with the specified JSON.
    *
    * @param json The default response data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[DefaultResponse] = {
    validateDefaultResponse.reads(json).fold(
      valid = { validated => JsSuccess(new DefaultResponse(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }
}
