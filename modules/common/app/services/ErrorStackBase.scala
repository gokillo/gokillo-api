/*#
  * @file ErrorStackBase.scala
  * @begin 19-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

trait ErrorStackBase {

  /** Converts any error to HTTP `Result`. */
  def toResult = ErrorStackBase.resolver
}

object ErrorStackBase {

  import play.api.mvc.Result
  import play.api.mvc.Results._
  import utils.common.typeExtensions._

  type Resolver = PartialFunction[(Throwable, Option[String]), Result]

  /**
    * Gets a `Resolver` that converts any error to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e, log) => log.foreach(WrappedLogger.error(_, e)); InternalServerError(e.toJson(errorCode = Some("internal_error")))
  }
}
