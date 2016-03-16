/*#
  * @file Formats.scala
  * @begin 13-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.data.validation.ValidationError

/**
  * Provides additional JSON formatters.
  */
object Formats {

  /**
    * Formats a path as a string.
    */
  implicit val jsPathWrites = Writes[JsPath](
    path => JsString(path.toString)
  )

  /**
    * Formats a validation error as a string.
    */
  implicit val validationErrorWrites = Writes[ValidationError](
    validationError => JsString(validationError.message)
  )

  /**
    * Combines a path and a validation error in a tuple.
    */
  implicit val jsonValidateErrorWrites = (
    (__ \ 'path).write[JsPath] ~
    (__ \ 'errors).write[Seq[ValidationError]]
  ).tupled

  /**
    * Serializes/Deserializes a `DateTime` to/from JSON.
    */
  implicit val jodaDateTimeFormat = new Format[DateTime] {
    def reads(json: JsValue) = {
      json.validate[String].map[DateTime](dateTime =>
        new DateTime(dateTime, DateTimeZone.UTC)
      )
    }
    def writes(dateTime: DateTime) = {
      JsString(ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC).print(dateTime))
    }
  }
}
