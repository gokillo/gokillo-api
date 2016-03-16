/*#
  * @file ErrorInfo.scala
  * @begin 8-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import com.wordnik.swagger.config.ConfigFactory
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import utils.common.Formats._

/**
  * Provides information about any error it might occur during application
  * runtime.
  *
  * @constructor  Initializes a new instance of the [[ErrorInfo]] class.
  * @param json   The error information as JSON.
  */
class ErrorInfo protected(protected var json: JsValue) extends JsModel {

  def apiVersion = json as (__ \ 'apiVersion).read[String]
  def errorCode = json as (__ \ 'errorCode).read[String]
  def message = json as (__ \ 'message).read[String]
  def details = json as (__ \ 'details).readNullable[JsValue]
  def time = json as (__ \ 'time).read[DateTime]

  def copy(json: JsValue) = new ErrorInfo(this.json.as[JsObject] ++ json.as[JsObject])
}

/**
  * Factory class for creating [[ErrorInfo]] instances.
  */
object ErrorInfo {

  import play.api.libs.functional.syntax._

  private val ApiVersion = ConfigFactory.config.getApiVersion

  /**
    * Initializes a new instance of the [[ErrorInfo]] class.
    *
    * @param errorCode  The error code.
    * @param message    The error message.
    * @param details    Optional error details as JSON.
    * @return           A new [[ErrorInfo]] instance.
    */
  def apply(
    errorCode: String,
    message: String,
    details: Option[JsValue] = None
  ) = new ErrorInfo(
    errorInfoWrites.writes(
      ApiVersion,
      errorCode,
      message,
      details,
      DateTime.now(DateTimeZone.UTC)
    )
  )

 /**
   * Extracts the content of the specified [[ErrorInfo]].
   *
   * @param error The [[ErrorInfo]] to extract the content from.
   * @return      An `Option` that contains the extracted data,
   *              or `None` if `error` is `null`.
   */
  def unapply(error: ErrorInfo) = {
    if (error eq null) null
    else Some((
      error.apiVersion,
      error.errorCode,
      error.message,
      error.details,
      error.time
    ))
  }

  /**
    * Serializes an [[ErrorInfo]] to JSON.
    * @note Used internally by `apply`.
    */
  private val errorInfoWrites = (
    (__ \ 'apiVersion).write[String] ~
    (__ \ 'errorCode).write[String] ~
    (__ \ 'message).write[String] ~
    (__ \ 'details).writeNullable[JsValue] ~
    (__ \ 'time).write[DateTime]
  ).tupled
}
