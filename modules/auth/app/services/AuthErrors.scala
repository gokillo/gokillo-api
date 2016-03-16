/*#
  * @file AuthErrors.scala
  * @begin 22-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.{BaseException, ErrorStackBase, WrappedLogger}

trait AuthErrors extends ErrorStackBase {

  /** Converts auth errors to HTTP `Result`. */
  override def toResult = AuthErrors.resolver orElse super.toResult
}

object AuthErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class AuthenticationExpired(subjectType: String, subject: String) extends BaseException(
    s"authentication of $subjectType $subject has expired"
  )
  final case class AuthenticationFailed(secretType: String, subjectType: String, subject: String) extends BaseException(
    s"$secretType provided by $subjectType $subject does not match"
  )
  final case class AuthenticationViolated(subjectType: String, subject: String) extends BaseException(
    s"authentication of $subjectType $subject has been violated"
  )
  final case class InvalidToken() extends BaseException(
    s"could not deserialize token from request header or query string"
  )
  final case class MissingProofs(proofs: List[String], user: String) extends BaseException(
    s"""proofs of ${proofs.mkString(", ")} for user $user ${if (proofs.length > 1) "are" else "is"} missing"""
  )
  final case class NotAuthorized(
    `type`: String, what: String, subjectType: String, subject: String, reason: String = null
  ) extends BaseException(
    s"${`type`} $what not authorized for $subjectType $subject: " + (if (reason != null) reason else s"$subjectType does not have required privileges")
  )
  final case class NotAuthenticated(`type`: String, what: String, subjectType: String) extends BaseException(
    s"${`type`} $what requires authentication but no $subjectType is authenticated"
  )
  final case class NotRegistered(subjectType: String, subject: String) extends BaseException(
    s"$subjectType $subject not registered"
  )
  final case class PasswordChangeFailed(subjectType: String, subject: String, reason: String) extends BaseException(
    s"password change for $subjectType $subject failed: $reason"
  )
  final case class AccountMismatch(metaAccountId: String) extends BaseException(
    s"peer account of meta-account $metaAccountId is missing"
  )
  final case class RegistrationNotComplete(subjectType: String, subject: String, reason: String) extends BaseException(
    s"registration of $subjectType $subject not complete: $reason"
  )
  final case class RequestViolated(request: String, subjectType: String, subject: String) extends BaseException(
    s"request $request submitted by $subjectType $subject has been violated"
  )

  /**
    * Gets a `Resolver` that converts auth errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(
      _: AuthenticationViolated |
      _: NotRegistered |
      _: RequestViolated
    ), log) => log.foreach(WrappedLogger.warn(_, e)); Forbidden(e.toJson())
    case (e@(_: AuthenticationExpired), log) => log.foreach(WrappedLogger.debug(_, e)); Forbidden(e.toJson())
    case (e@(_: NotAuthenticated), log) => log.foreach(WrappedLogger.debug(_, e)); Unauthorized(e.toJson())
    case (e@(_: NotAuthorized), log) => log.foreach(WrappedLogger.warn(_, e)); Unauthorized(e.toJson())
    case (e@(
      _: AuthenticationFailed |
      _: InvalidToken |
      _: MissingProofs |
      _: PasswordChangeFailed |
      _: RegistrationNotComplete
    ), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
  }
}
