/*#
  * @file CoreErrors.scala
  * @begin 1-Jun-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import services.common.{BaseException, ErrorStackBase, WrappedLogger}

trait CoreErrors extends ErrorStackBase {

  /** Converts core errors to HTTP `Result`. */
  override def toResult = CoreErrors.resolver orElse super.toResult
}

object CoreErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class InvalidTrainData(fields: String*) extends BaseException(
    s"""train data should be in CSV format and must define the following fields: ${fields.mkString(", ")}"""
  )

  /**
    * Gets a `Resolver` that converts core errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(_: InvalidTrainData), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
  }
}
