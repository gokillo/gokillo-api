/*#
  * @file CorsStrategy.scala
  * @begin 28-Dec-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package filters

import play.api.http.HeaderNames._
import play.api.mvc.RequestHeader

/**
  * Defines a strategy for cross-origin resource sharing (CORS).
  *
  * A `CorsStrategy` is a `RequestHeader => Option[String]` that returns an `Option` value
  * containing the origin if the request is allowed or `None` if it isn't.
  *
  * @param handler  The function that determines whether or not to allow a request.
  */
abstract class CorsStrategy(handler: RequestHeader => Option[String]) extends (RequestHeader => Option[String]) {

  override def apply(request: RequestHeader): Option[String] = handler(request)
}

/**
  * Default strategy implementations.
  */
object CorsStrategy {

  private val origin = (request: RequestHeader) => request.headers.get(ORIGIN)
  private def localhost(s: String) = s.contains("localhost") || s.contains("127.0.0.1")
  private def port(ps: Int*) = (s: String) => ps.exists(p => s.endsWith(s":${p.toString}"))

  /**
    * Allows any request, event if malformed.
    */
  object Everyone extends CorsStrategy(_ => Some("*"))

  /**
    * Allows requests with an `Origin` header.
    * @note Same as `Everyone` for valid requests.
    */
  object Origin extends CorsStrategy(origin)

  /**
    * Allows no requests.
    */
  object NoOne extends CorsStrategy(_ => None)

  /**
    * Allows only requests from localhost.
    */
  object Localhost extends CorsStrategy(origin andThen (_.filter(localhost)))

  /**
    * Allows only requests from localhost that originate from one of the specified ports.
    * @param ports The ports to allow requests from.
    */
  case class Localhost(ports: Int*) extends CorsStrategy(origin andThen (_.filter(localhost).filter(port(ports: _*))))

  /**
    * Allows only requests from the specified origins.
    *
    * @param origins  The origins to allow requests from.
    * @note           This results in white-listing behaviour on the browser side.
    */
  case class Fixed(origins: String*) extends CorsStrategy(origin andThen (_.map(_ => origins.mkString(","))))

  /**
    * Allows only requests from domains in the specified white-list.
    *
    * @param domains  The domains to allow requests from.
    * @note           The list is resolved on the server side.
    */
  case class WhiteList(domains: String*) extends CorsStrategy(origin andThen (_.filter(domains.contains)))

  /**
    * Allows only requests from domains not in the specified black-list.
    *
    * @param domains  The domains not to allow requests from.
    * @note           The list is resolved on the server side.
    */
  case class BlackList(domains: String*) extends CorsStrategy(origin andThen (_.filterNot(domains.contains)))

  /**
    * Allows only requests that satisfy the conditions defined by the specified handler.
    *
    * @param handler  A function that returns a Boolean value indicating whether or not
    *                 a given request is allowed.
    * @param allowed  A function that returns the origin associated with a given a request.
    */
  case class Satisfies(handler: (RequestHeader) => Boolean, allowed: (RequestHeader) => String = _ => "*")
    extends CorsStrategy(request => if (handler(request)) Some(allowed(request)) else None) {

    def allowing(origin: String) = Satisfies(handler, _ => origin)
    def withOrigin = Satisfies(handler, _.headers(ORIGIN))
  }

  /**
    * Allows only requests that satisfy the conditions defined by the specified partial function.
    *
    * @param handler  A partial function that matches and returns the origin if a given request
    *                 is allowed.
    */
  case class CustomPF(handler: PartialFunction[RequestHeader, String]) extends CorsStrategy(handler.lift)

  /**
    * Allows only requests that satisfy the conditions defined by the specified function.
    *
    * @param handler  A function that returns an `Option` value containing the origin if a given
    *                 request is allowed or `None` if it isn't.
    */
  case class Custom(handler: RequestHeader => Option[String]) extends CorsStrategy(handler)
}
