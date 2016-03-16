/*#
  * @file BaseException.scala
  * @begin 29-Jun-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import scala.util.control.NoStackTrace
import play.api.libs.json.JsValue

/**
  * Base exception class with stack trace disabled.
  *
  * @constructor    Initializes a new instance of the `BaseException` class
  *                 with the specified message.
  * @param message  The error message.
  * @return         A new instance of the `BaseException` class.
  */
abstract class BaseException(message: String) extends Exception(message) with NoStackTrace {

  /**
    * Optional error details as JSON.
    */
  var details: Option[JsValue] = None
}
