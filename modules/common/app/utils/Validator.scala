/*#
  * @file Validator.scala
  * @begin 21-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.util.control.NonFatal
import play.api.Play.current
import play.api.i18n.Lang
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.data.validation.ValidationError
import utils.common.typeExtensions._

/**
  * Defines common validators for JSON values.
  */
trait Validator {

  /**
    * Verifies the number read by the specified JSON deserializer is
    * greater than zero.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated number,
    *               or `JsError` if the number is not greater than zero.
    */
  def greaterThanZero(implicit reads: Reads[Double]) = Reads[Double](js =>
    reads.reads(js).flatMap { value =>
      if (value > 0.0) JsSuccess(value)
      else JsError(ValidationError("error.greaterThanZero"))
    }
  )

  /**
    * Verifies the number read by the specified JSON deserializer is
    * equal or greater than the specified value.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated number,
    *               or `JsError` if the number is not equal to or greater than `d`.
    */
  def equalOrGreaterThan(d: Double)(implicit reads: Reads[Double]) = Reads[Double](js =>
    reads.reads(js).flatMap { value =>
      if (value >= d) JsSuccess(value)
      else JsError(ValidationError("error.lessThan", d))
    }
  )

  /**
    * Verifies the number read by the specified JSON deserializer is
    * equal or less than the specified value.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated number,
    *               or `JsError` if the number is not equal to or less than `d`.
    */
  def equalOrLessThan(d: Double)(implicit reads: Reads[Double]) = Reads[Double](js =>
    reads.reads(js).flatMap { value =>
      if (value <= d) JsSuccess(value)
      else JsError(ValidationError("error.greaterThan", d))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is a
    * coin address.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not a coin address.
    */
  def coinAddress(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isCoinAddress) JsSuccess(value)
      else JsError(ValidationError("error.coinAddress"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is
    * either an email address or a wildcard.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is neither an email
    *               address nor a wildcard.
    */
  def emailAddressOrWildcard(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isEmailAddress || value == "*") JsSuccess(value)
      else JsError(ValidationError("error.emailAddress"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is an
    * object id.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not an object id.
    */
  def objectId(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isObjectId) JsSuccess(value)
      else JsError(ValidationError("error.objectId"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is a
    * universally unique identifier.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not a universally unique identifier.
    */
  def uuid(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isUuid) JsSuccess(value)
      else JsError(ValidationError("error.uuid"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is a
    * phone number.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not a phone number.
    */
  def phoneNumber(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isPhoneNumber) JsSuccess(value)
      else JsError(ValidationError("error.phoneNumber"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is an
    * ISO 8601 date.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not an ISO 8601 date.
    */
  def isoDate(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isIsoDate) JsSuccess(value)
      else JsError(ValidationError("error.isoDate"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is an
    * ISO 8601 timestamp.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not an ISO 8601 timestamp.
    */
  def isoDateTime(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isIsoDateTime) JsSuccess(value)
      else JsError(ValidationError("error.isoDateTime"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is an
    * ISO 639-2 language code, optionally followed by an ISO 3166-1 alpha-2
    * country code.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not an ISO langauge code.
    */
  def isoLang(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      var lang: Lang = null
      try { lang = Lang(value) } catch { case NonFatal(_) => }

      if (lang != null && Lang.availables.contains(lang)) JsSuccess(value)
      else JsError(ValidationError("error.isoLang"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is a
    * standard zone offset.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not a standard zone offset.
    */
  def timeZone(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isTimeZone) JsSuccess(value)
      else JsError(ValidationError("error.timeZone"))
    }
  )

  /**
    * Verifies the value read by the specified JSON deserializer is a
    * website address.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the validated value,
    *               or `JsError` if the value is not a website address.
    */
  def website(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      if (value.isWebsite) JsSuccess(value)
      else JsError(ValidationError("error.website"))
    }
  )

  /**
    * Converts the value read by the specified JSON deserializer to a number.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the converted value,
    *               or `JsError` if the value is not a number.
    */
  def toNumber(implicit reads: Reads[String]) = Reads[JsNumber](js =>
    reads.reads(js).flatMap { value =>
      parse[Double](value) match {
        case Some(number) => JsSuccess(JsNumber(number))
        case _ => JsError(ValidationError("error.number", value))
      }
    }
  )

  /**
    * Converts a string read by the specified JSON deserializer to upper case.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the converted value,
    *               or `JsError` if the value is not a string.
    */
  def toUpperCase(implicit reads: Reads[String]) = Reads[JsString](js =>
    reads.reads(js).flatMap { _ match {
      case value: String => JsSuccess(JsString(value.toUpperCase))
      case value => JsError(ValidationError("error.string", value))
    }}
  )

  /**
    * Converts a string read by the specified JSON deserializer to lower case.
    *
    * @param reads  An implicit JSON deserializer.
    * @return       A `JsResult` that contains the converted value,
    *               or `JsError` if the value is not a string.
    */
  def toLowerCase(implicit reads: Reads[String]) = Reads[JsString](js =>
    reads.reads(js).flatMap { _ match {
      case value: String => JsSuccess(JsString(value.toLowerCase))
      case value => JsError(ValidationError("error.string", value))
    }}
  )
}
