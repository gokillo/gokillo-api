/*#
  * @file Responses.scala
  * @begin 10-Apr-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import play.api.libs.json._
import models.common.ErrorInfo
import models.common.ResponseBody

/**
  * Provides functionality for generating responses to requests.
  */
object Responses {

  /**
    * Returns a `ResponseBody` that represents success.
    * @return The response as JSON.
    */
  def success: JsValue = success(Json.obj())
  def success(response: JsValue): JsValue = ResponseBody("success", response).asJson

  /**
    * Returns a `ResponseBody` that represents failure.
    * @return The response as JSON.
    */
  def error(errorInfo: ErrorInfo): JsValue = error(errorInfo.asJson)
  def error(json: JsValue): JsValue = ResponseBody("error", Json.obj("error" -> json)).asJson
}
