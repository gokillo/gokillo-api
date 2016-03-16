/*#
  * @file CommonErrors.scala
  * @begin 19-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

trait CommonErrors extends ErrorStackBase {

  /** Converts common errors to HTTP `Result`. */
  override def toResult = CommonErrors.resolver orElse super.toResult
}

object CommonErrors {

  import play.api.libs.json.{Json, JsValue}
  import play.api.mvc.Results
  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class ElementNotFound(elementType: String, element: String, subjectType: String, subject: String) extends BaseException(
    s"$elementType $element of $subjectType $subject not found"
  )
  final case class ElementNotRemoveable(elementType: String, element: String, subjectType: String, subject: String) extends BaseException(
    s"$elementType $element of $subjectType $subject not removeable"
  )
  final case class EmptyList(elementType: String, subjectType: String = null, subject: String = null) extends BaseException(
    s"no $elementType found" + (if (subjectType != null && subject != null) s" for $subjectType $subject" else "")
  )
  final case class InvalidRequest(url: String, validationErrors: JsValue) extends BaseException(
    s"request to endpoint $url contains invalid or malformed data"
  ) { details = Some(validationErrors) }
  final case class InvalidResponse(url: String, validationErrors: JsValue) extends BaseException(
    s"response from endpoint $url contains invalid or malformed data"
  ) { details = Some(validationErrors) }
  case class NotDefined(`type`: String, what: String) extends BaseException(
    s"${`type`} $what not defined"
  )
  case class NotFound(`type`: String, what: String = null) extends BaseException(
    s"${`type`} ${if (what != null) what + " " else ""}not found"
  )
  final case class NotSupported(`type`: String, what: String) extends BaseException(
    s"${`type`} $what not supported"
  )
  final case class NotAllowed(`type`: String, what: String, reason: String = null) extends BaseException(
    s"${`type`} $what not allowed" + (if (reason != null) s": $reason" else "")
  )
  final case class NotImplemented(`type`: String, what: String) extends BaseException(
    s"${`type`} $what not implemented"
  )
  final case class UnreferencedObject(objectType: String, objectId: String) extends BaseException(
    s"unreferenced $objectType: $objectId"
  )
  final case class MissingConfig(config: String) extends BaseException(
    s"configuration $config is missing"
  )

  /**
    * Gets a `Resolver` that converts common errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(_: InvalidRequest), log) => log.foreach(l => WrappedLogger.debug(l, e)); UnprocessableEntity(e.toJson())
    case (e@(_: InvalidResponse), log) => log.foreach(l => WrappedLogger.debug(l, e)); InternalServerError(e.toJson())
    case (e@(_: NotImplemented), _) => Results.NotImplemented(e.toJson())
    case (e@(
      _: ElementNotRemoveable |
      _: NotAllowed |
      _: NotSupported
    ), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
    case (e@(
      _: ElementNotFound |
      _: EmptyList |
      _: NotFound
    ), log) => log.foreach(WrappedLogger.debug(_, e)); Results.NotFound(e.toJson())
  }
}
