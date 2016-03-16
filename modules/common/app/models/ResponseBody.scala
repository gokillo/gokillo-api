/*#
  * @file ResponseBody.scala
  * @begin 10-Apr-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._

/**
  * Represents the body of a response.
  *
  * @constructor  Initializes a new instance of the [[ResponseBody]] class.
  * @param json   The body consisting of result information and response
  *               data as JSON.
  */
class ResponseBody protected(protected var json: JsValue) extends JsModel {

  def result = json as (__ \ 'result).read[String]
  def result_= (v: String) = setValue((__ \ 'result), Json.toJson(v))

  def copy(json: JsValue) = new ResponseBody(this.json.as[JsObject] ++ json.as[JsObject])
}

/**
  * Factory class for creating [[ResponseBody]] instances.
  */
object ResponseBody {

  import play.api.libs.functional.syntax._

  /**
    * Initializes a new instance of the [[ResponseBody]] class.
    *
    * @param result The result of the request (e.g. "success", "error").
    * @param json   The response to the request as JSON.
    * @return       A new [[ResponseBody]] instance.
    */
  def apply(result: String, json: JsValue) = {
    new ResponseBody(Json.obj("result" -> result) ++ json.as[JsObject])
  }

 /**
   * Extracts the content of the specified [[ResponseBody]].
   *
   * @param responseBody  The [[ResponseBody]] to extract the content from.
   * @return              An `Option` that contains the extracted data,
   *                      or `None` if `responseBody` is `null`.
   */
  def unapply(responseBody: ResponseBody) = {
    if (responseBody eq null) null
    else Some((
      responseBody.result
    ))
  }

  /**
    * Serializes a [[ResponseBody]] to JSON.
    */
  implicit val responseBodyWrites = new Writes[ResponseBody] {
    def writes(responseBody: ResponseBody) = responseBody.json
  }
}
