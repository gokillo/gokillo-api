/*#
  * @file MessagingErrors.scala
  * @begin 7-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import services.common.{BaseException, ErrorStackBase, WrappedLogger}

trait MessagingErrors extends ErrorStackBase {

  /** Converts messaging errors to HTTP `Result`. */
  override def toResult = MessagingErrors.resolver orElse super.toResult
}

object MessagingErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class AlreadySent(`type`: String, id: String) extends BaseException(
    s"""${`type`} $id already sent"""
  )

  /**
    * Gets a `Resolver` that converts messaging errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(_: AlreadySent), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
  }
}
