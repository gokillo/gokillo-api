/*#
  * @file Status.scala
  * @begin 16-Aug-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._

/**
  * Represents the status of an entity.
  *
  * @constructor  Initializes a new instance of the [[Status]] class.
  * @param json   The status data as JSON.
  */
class Status protected(protected var json: JsValue) extends JsModel with api.Status {

  def value = json as (__ \ 'value).read[String]
  def value_= (v: String) = setValue((__ \ 'value), Json.toJson(v))
  def timestamp = json as (__ \ 'timestamp).read[DateTime]
  def timestamp_= (v: DateTime) = setValue((__ \ 'timestamp), Json.toJson(v))

  def copy(status: Status): Status = new Status(this.json.as[JsObject] ++ status.json.as[JsObject])
  def copy(json: JsValue): Status = Status(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(status, _) => status
    case JsError(_) => new Status(this.json)
  }

  override def toString = value
}

/**
  * Factory class for creating [[Status]] instances.
  */
object Status extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError

  /**
    * Initializes a new instance of the [[Status]] class with the specified JSON.
    *
    * @param json The status data as JSON.
    * @return     A `JsResult` value that contains the new class
    *             instance, or `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Status] = {
    validateStatus.reads(json).fold(
      valid = { validated => JsSuccess(new Status(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Status]] class with the specified values.
    *
    * @param value      The value of the status.
    * @param timestamp  The time the status was set. Default to now.
    * @return           A new instance of the [[Status]] class.
    */
  def apply(
    value: String,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC)
  ): Status = new Status(
    statusWrites.writes(
      value,
      timestamp
    )
  )

  /**
    * Extracts the content of the specified [[Status]].
    *
    * @param status The [[Status]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `status` is `null`.
    */
  def unapply(status: Status) = {
    if (status eq null) None
    else Some((
      status.value,
      status.timestamp
    ))
  }

  /**
    * Serializes/Deserializes a [[Status]] to/from JSON.
    */
  implicit val statusFormat = new Format[Status] {
    def reads(json: JsValue) = Status(json)
    def writes(status: Status) = status.json
  }

  /**
    * Implicitly converts the specified status to a string.
    *
    * @param status The status to convert.
    * @return       The string converted from `status`.
    */
  implicit def toString(status: Status) = status.toString

  /**
    * Serializes a [[Status]] to JSON.
    * @note Used internally by `apply`.
    */
  val statusWrites = (
    (__ \ 'value).write[String] ~
    (__ \ 'timestamp).write[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of a [[Status]].
    *
    * @return     A `Reads` that validates the JSON representation of a [[Status]].
    * @note       This validator is intended for JSON coming from both inside and
    *             outside the application.
    */
  val validateStatus = (
    ((__ \ 'value).json.pickBranch) ~
    ((__ \ 'timestamp).json.pickBranch(Reads.of[JsString] <~ isoDateTime))
  ).reduce
}
