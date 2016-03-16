/*#
  * @file typeExtensions.scala
  * @begin 31-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import play.api.libs.json._
import java.util.regex.Pattern

/**
  * Extends a set of types with custom functionality.
  *
  * Usage example:
  *
  * {{{
  * import utils.common.typeExtensions._
  *
  * object MyApp extends App {
  *
  * if (args(0).isEmailAddress)
  *   println("%s is an email address".format(args(0)))
  * else if (args(0).isPhoneNumber)
  *   println("%s is a phone number".format(args(0)))
  * else
  *   println("Undefined argument")
  * }}}
  */
package object typeExtensions {

  /** Specifies the default significand precision of a `Double`. */
  final val DefaultPrecision = Precision(0.00000001)

  /**
    * Provides custom operators to `Double` with significand precision.
    *
    * @constructor  Initializes a new instance of the `Precision` class with the
    *               specified value.
    * @param value  The significand precision.
    * @return       A new instance of the `Precision` class.
    */
  case class Precision(value: Double) {

    /** Gets the length of the significand. */
    def length = (math.floor(math.log(value) / math.log(10)).abs).toInt
  }

  /**
    * Extends `Double` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[DoubleWithExtensions]] class.
    * @param d      The `Double` instance to extend with custom functionality.
    */
  implicit class DoubleWithExtensions(val d: Double) extends AnyVal {

    def ~(implicit precision: Precision = DefaultPrecision) = { val s = math.pow(10, precision.length); (math.floor(d * s)) / s }
    def ~~(implicit precision: Precision = DefaultPrecision) = { BigDecimal(d).setScale(precision.length, BigDecimal.RoundingMode.HALF_UP).toDouble }
    def ~=(d2: Double)(implicit precision: Precision = DefaultPrecision) = (d - d2).abs < precision.value
    def ~>(d2: Double)(implicit precision: Precision = DefaultPrecision) = !(~=(d2)) && (d - d2) > precision.value
    def ~<(d2: Double)(implicit precision: Precision = DefaultPrecision) = !(~=(d2)) && (d - d2) < precision.value
    def ~>=(d2: Double)(implicit precision: Precision = DefaultPrecision) = ~=(d2) || ~>(d2)
    def ~<=(d2: Double)(implicit precision: Precision = DefaultPrecision) = ~=(d2) || ~<(d2)
  }

  /**
    * Extends `List[T]` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[ListWithExtensions]] class.
    * @param l      The `List[T]` instance to extend with custom functionality.
    */
  implicit class ListWithExtensions[T](val l: List[T]) extends AnyVal {

    def containsDuplicates: Boolean = {
      val seen = scala.collection.mutable.HashSet[T]()
      for (e <- l) if (seen(e)) return true else seen += e
      false
    }
  }

  /**
    * Extends `String` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[StringWithExtensions]] class.
    * @param s      The `String` instance to extend with custom functionality.
    */
  implicit class StringWithExtensions(val s: String) extends AnyVal {

    /**
      * Determines whether a `String` is an email address.
      *
      * @return `true` if the `String` is an email address; otherwise, `false`.
      */
    def isEmailAddress = """^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is a phone number.
      *
      * @return `true` if the `String` is a phone number; otherwise, `false`.
      */
    def isPhoneNumber = """^[+]?([0-9]*[\.\s\-\(\)]|[0-9]+){3,24}$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is an object id.
      *
      * @return `true` if the `String` is an object id; otherwise, `false`.
      */
    def isObjectId = """^[0-9a-zA-Z]{24}$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is a UUID.
      *
      * @return `true` if the `String` is a UUID; otherwise, `false`.
      */
    def isUuid = """^[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is a coin address.
      *
      * @return `true` if the `String` is a coin address; otherwise, `false`.
      */
    def isCoinAddress = """^[13][a-km-zA-HJ-NP-Z0-9]{26,33}$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is an ISO 8601 date.
      *
      * @return `true` if the `String` is an ISO 8601 date; otherwise, `false`.
      */
    def isIsoDate = """^(\d{4})-((0[1-9])|(1[0-2]))-(0[1-9]|[12][0-9]|3[01])$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is an ISO 8601 timestamp.
      *
      * @return `true` if the `String` is an ISO 8601 timestamp; otherwise, `false`.
      */
    def isIsoDateTime = """^(\d{4})-((0[1-9])|(1[0-2]))-(0[1-9]|[12][0-9]|3[01])T(0[0-9]|1[0-9]|2[0-3]):(0[0-9]|1[0-9]|2[0-9]|3[0-9]|4[0-9]|5[0-9]):(0[0-9]|1[0-9]|2[0-9]|3[0-9]|4[0-9]|5[0-9])(\.\d{3}|)(Z|(-|\+)(0[0-9]|1[0-9]|2[0-3]):(0[0-9]|1[0-9]|2[0-9]|3[0-9]|4[0-9]|5[0-9]))$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is a standard time zone offset.
      *
      * @return `true` if the `String` is a standard time zone offset; otherwise, `false`.
      */
    def isTimeZone = """^(?:Z|[+-](?:2[0-3]|[01][0-9]):[0-5][0-9])$""".r.unapplySeq(s).isDefined

    /**
      * Determines whether a `String` is a host name.
      *
      * @return `true` if the `String` is a host name; otherwise, `false`.
      */
    def isHostName = """^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])(:\d+)?$""".r.unapplySeq(s)isDefined

    /**
      * Determines whether a `String` is a website address.
      *
      * @return `true` if the `String` is a website address; otherwise, `false`.
      */
    def isWebsite = """^(https?:\/\/)?([\da-z\.-]+)\.([a-z\.]{2,6})([\/\w \.-]*)*\/?$""".r.unapplySeq(s).isDefined

    /**
      * Splits a `String` after the specified pattern.
      *
      * @param pattern  The pattern to search for.
      * @return         A `Tuple2` containing the `String` up to `pattern`
      *                 and the `String` after `pattern.
      */
    def splitAfter(pattern: String): (String, String) = {
      val r = (Pattern quote pattern).r
      r.findFirstMatchIn(s).map { m =>
        (s.substring(0, m.end), m.after.toString)
      }.getOrElse(s, "")
    }

    /**
      * Splits a `String` before the specified pattern.
      *
      * @param pattern  The pattern to search for.
      * @return         A `Tuple2` containing the `String` before `pattern`
      *                 and the `String` starting from `pattern.
      */
    def splitBefore(pattern: String): (String, String) = {
      val r = (Pattern quote pattern).r
      r.findFirstMatchIn(s).map { m =>
        (s.substring(0, m.start), pattern + m.after.toString)
      }.getOrElse(s, "")
    }

    /**
      * Strips punctuation from a `String`.
      *
      * @return The `String` with punctuation stripped.
      */
    def stripPunctuation = s.replaceAll("""(?m)[\.,:;\?!]""", "")

    /**
      * Truncates a `String` after the specified pattern.
      *
      * @param pattern  The pattern after which to truncate the `String`.
      * @return         The truncated `String`.
      */
    def truncateAfter(pattern: String) = splitAfter(pattern)._1

    /**
      * Truncates a `String` before the specified pattern.
      *
      * @param pattern  The pattern before which to truncate the `String`.
      * @return         The truncated `String`.
      */
    def truncateBefore(pattern: String) = splitBefore(pattern)._1

    /**
      * Converts the first character of a `String` to lower case.
      *
      * @return The `String` with the first character in lower case.
      */
    def uncapitalize = {
      if (s == null || s.length == 0) ""
      else {
        val chars = s.toCharArray
        chars(0) = chars(0).toLower
        new String(chars)
      }
    }
  }

  /**
    * Extends `JsValue` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[JsValueWithExtensions]] class.
    * @param json   The `JsValue` instance to extend with custom functionality.
    */
  implicit class JsValueWithExtensions(val json: JsValue) extends AnyVal {

    /**
      * Flattens a `JsValue`.
      * @return The transformed JSON.
      */
    def flatten = {
      def concat(parent: String, key: String) = if (parent.nonEmpty) s"$parent.$key" else key

      def flatten(js: JsValue,  parent: String = ""): Seq[JsValue] = js.as[JsObject].fieldSet.toSeq.flatMap {
        case (key, values) => values match {
          case JsBoolean(v) => Seq(Json.obj(concat(parent, key) -> v))
          case JsNumber(v) => Seq(Json.obj(concat(parent, key) -> v))
          case JsString(v) => Seq(Json.obj(concat(parent, key) -> v))
          case JsArray(seq) => seq.zipWithIndex.flatMap{ case (v, i) => flatten(v, concat(parent, s"$key[$i]")) }
          case v: JsObject => flatten(v, concat(parent, key))
          case _ => Seq(Json.obj(concat(parent, key) -> JsNull))
        }
      }

      flatten(json).foldLeft(Json.obj())((obj, other) => obj.deepMerge(other.as[JsObject]))
    }

    /**
      * Determines whether or not a `JsValue` has a value.
      * @return `true` if the `JsValue` has a value; otherwise, `false`.
      */
    def hasValue = json match {
      case JsNull => false
      case JsString("") => false
      case _ => true
    }

    /**
      * Sorts the fields of a `JsValue` alphabetically.
      * @return The sorted `JsValue`.
      */
    def sort: JsValue = {
      def _sort(js: JsValue): JsValue = js match {
        case JsObject(fields) => JsObject(fields.sortBy(_._1).map { case (k, v) => (k, _sort(v)) })
        case _ => js
      }
      
      _sort(json)
    }
  }

  /**
    * Extends `Throwable` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[ThrowableWithExtensions]] class.
    * @param t      The `Throwable` instance to extend with custom functionality.
    */
  implicit class ThrowableWithExtensions(val t: Throwable) extends AnyVal {

    import com.google.common.base.CaseFormat
    import utils.common.Responses.error
    import models.common.ErrorInfo
    import services.common.BaseException

    /**
      * Converts a `Throwable` to JSON.
      *
      * @param errorCode  An optional error code; if omitted, the actual `Throwable` name is
      *                   transformed to a snake-case error code.
      * @return           The JSON representation of the `Throwable`.
      */
    def toJson(errorCode: Option[String] = None) = {
      val _errorCode = errorCode getOrElse {
        val className = t.getClass.getName
        CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, className.substring(className.lastIndexOf("$") + 1))
      }

      val details = t match {
        case e: BaseException => e.details
        case _ => None
      }

      error(ErrorInfo(_errorCode, t.getMessage, details))
    }
  }

  /**
    * Extends `Reads` instances with custom functionality.
    *
    * @constructor  Initialized a new instance of the [[ReadsWithExtensions]] class.
    * @param reads  The `Reads` instance to extend with custom functionality.
    */
  implicit class ReadsWithExtensions(val reads: Reads[JsObject]) extends AnyVal {

    /**
      * Returns either a successful `Reads` or an empty object.
      * @return Either a successful `Reads` or an empty object.
      */
    def orEmpty = Reads[JsObject](js =>
      reads.reads(js) match {
        case JsError(errors) => errors.head._2.head.message match {
          case "error.path.missing" => JsSuccess(Json.obj())
          case _ => JsError(errors)
        }
        case success => success
      }
    )

    /**
      * Returns either a successful `Reads` or an empty object if allowed.
      *
      * @param b  A Boolean value indicating  whether or not an empty
      *           object is allowed when a `Reads` fails.
      * @return   Either a successful `Reads` or an empty object if `b`
      *           is `true`.
      */
    def orEmptyIf(b: Boolean) = if (b) orEmpty else reads
  }
}
