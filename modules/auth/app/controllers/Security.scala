/*#
  * @file Security.scala
  * @begin 3-Jun-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.auth

import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import java.nio.charset.{StandardCharsets => SC}
import brix.crypto.Secret
import play.utils.UriEncoding
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._
import play.api.http.{HeaderNames, HttpVerbs}
import utils.common.env.localhost
import utils.common.RequestHelper._
import services.auth.{AuthErrors, AuthPlugin}
import models.auth.{Token, Role}
import models.auth.TokenType._
import models.auth.Role._

/**
  * Provides functionality for creating secure actions.
  */
trait Security { self: Controller =>

  import Security._

  protected val errors: AuthErrors

  /**
    * Creates an authenticated action.
    *
    * @param action A function that takes a `Token` and returns an `EssentialAction`.
    * @return       An authenticated action.
    */
  def Authenticated(action: Token => EssentialAction) = EssentialAction { implicit request =>
    val jwt = request.headers.get(HeaderNames.AUTHORIZATION) match {
      case Some(header) => s"""$AuthScheme (.*)""".r.unapplySeq(header).map(_.head.trim)
      case _ => request.getQueryString("auth").map(UriEncoding.decodePath(_, SC.US_ASCII.name))
    }

    jwt match {
      case Some(t) =>
        val auth = t.split(":") // auth(0) => token, auth(1) => signature
        val futureToken = AuthPlugin.token(auth(0))

        /*
         * service tokens (e.g. activation, reset) are discarded immediately after they
         * trigger the action they are associated with
         */
        futureToken.onSuccess { case token =>
          if (token.tokenType != Authorization && request.method != HttpVerbs.GET) {
            AuthPlugin.discardToken(token.id)
          }
        }

        Iteratee.flatten(futureToken.map {
          case token if !token.isValid => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.AuthenticationViolated("user", token.username),
              Some(s"request $requestUriWithMethod refused since token has been tampered: ${token.asJson.toString}")
            ))
          case token if token.isExpired => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.AuthenticationExpired("user", token.username),
              Some(s"request $requestUriWithMethod refused since token has expired: ${token.asJson.toString}")
            ))
          case token => if (token.apiKey == Token.BootstrapKey) action(token)(request) else {
            Secret(token.apiKey).sign(auth(0) + requestUriWithMethod) match {
              case Success(signed) =>
                if (signed == auth(1)) action(token)(request)
                else Done[Array[Byte], Result](errors.toResult(
                  AuthErrors.RequestViolated(requestUriWithMethod, "user", token.username),
                  Some(s"request $requestUriWithMethod refused since request has been tampered")
                ))
              case Failure(e) => throw e // forward exception to global error fallback
            }
          }
        }.recover {
          case e: java.text.ParseException => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.InvalidToken(),
              Some(s"could not parse token $jwt")
            ))
          case e => throw e // forward exception to global error fallback
        })
      case _ => Done(errors.toResult(
          AuthErrors.NotAuthenticated("request", requestUriWithMethod, "user"),
          Some(s"request $requestUriWithMethod refused since no token has been provided")
        ).withHeaders(HeaderNames.WWW_AUTHENTICATE -> AuthScheme))
    }
  }

  /**
    * Creates an authorized action.
    *
    * @param operation  The name of the operation to authorize.
    * @param tokenTypes One or more of the `TokenType` values.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  def Authorized(operation: String, tokenTypes: TokenType*)(action: Token => EssentialAction): EssentialAction = Authenticated { token =>
    EssentialAction { implicit request =>
      val _tokenTypes = if (tokenTypes.length > 0) tokenTypes else Seq(Authorization)
      if (!_tokenTypes.contains(token.tokenType)) Done(errors.toResult(
        AuthErrors.NotAuthorized("request", requestUriWithMethod, "user", token.username, "token of wrong type"),
        Some(s"""request $requestUriWithMethod not authorized since token is not of type ${_tokenTypes.mkString(", ")}: ${token.asJson.toString}""")
      )) else {
        val roles = securityProfiles(toFullyQualifiedName(operation))
        if (roles.contains(Role.id("any")) || token.roles.contains(Role.Superuser.id) || token.roles.exists(roles.contains(_))) {
          /*
           * action does not require any specific privilege or
           * current subject has all privileges or
           * current subject has required privileges
           */
          Logger.debug(s"request $requestUriWithMethod authorized for user ${token.subject}")
          action(token)(request)
        } else Done(errors.toResult(
          AuthErrors.NotAuthorized("request", requestUriWithMethod, "user", token.username),
          Some(s"""request $requestUriWithMethod not authorized since user does not have role(s) ${roles.mkString(", ")}: ${token.asJson.toString}""")
        ))
      }
    }   
  }
}

/**
  * Helper to deal with security profiles.
  */
object Security {

  import scala.collection.mutable.Map
  import scala.collection.JavaConversions._
  import scala.util.matching.Regex
  import play.api.Play.current
  import play.api.Play.configuration

  final val AuthScheme = "Gok!llo"
  private val OperationPrefixPattern = """^/[a-zA-Z0-9_\-]+/[a-zA-Z0-9_\-]+(/|$)""".r
  private val securityProfiles = Map[String, List[Int]]().withDefaultValue(List.empty)

  configuration.getConfigList("auth.securityProfiles").foreach { _.toList.foreach { config =>
    config.getString("operation").map { operation =>
      securityProfiles += (operation -> config.getStringList("roles").map {
        _.toList.map(Role.id(_))
      }.getOrElse(List.empty))
    }
  }}

  /**
    * Returns the fully qualified name for the specified operation,
    * according to the current HTTP request.
    *
    * @param operation  The operation to construct the fully qualified name for.
    * @param request    The current HTTP request.
    * @return           The fully qualified name for `operation`.
    */
  def toFullyQualifiedName(operation: String)(implicit request: RequestHeader) = {{
    OperationPrefixPattern.findFirstIn(request.path) match {
      case Some(prefix) if !prefix.endsWith("/") => s"$prefix/"
      case Some(prefix) => prefix
      case _ => "/"
    }} + operation
  }
}
