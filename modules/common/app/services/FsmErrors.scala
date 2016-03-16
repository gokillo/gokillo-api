/*#
  * @file FsmErrors.scala
  * @begin 20-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

trait FsmErrors extends ErrorStackBase {

  /** Converts fsm errors to HTTP `Result`. */
  override def toResult = FsmErrors.resolver orElse super.toResult
}

object FsmErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class InvalidMessage(message: String, state: String, reason: String) extends BaseException(
    s"message $message sent to fsm in state $state is invalid: $reason"
  )
  final case class InvalidState(`type`: String, what: String, state: String) extends BaseException(
    s"${`type`} $what is in an invalid state: $state"
  )
  final case class NotFoundInState(`type`: String, what: String, state: String) extends BaseException(
    s"${`type`} $what in state $state not found"
  )
  final case class TransductionNotPossible(`type`: String, what: String, newState: String, reason: String) extends BaseException(
    s"could not transduce ${`type`} $what to state $newState: $reason"
  )

  /**
    * Gets a `Resolver` that converts fsm errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(_: NotFoundInState), log) => log.foreach(WrappedLogger.debug(_, e)); NotFound(e.toJson())
    case (e@(_: TransductionNotPossible), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
  }
}
