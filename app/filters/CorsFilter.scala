/*#
  * @file CorsFilter.scala
  * @begin 28-Dec-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package filters

import scala.concurrent.Future
import play.api.http.HeaderNames._
import play.api.http.HttpVerbs._
import play.api.mvc.{Filter, Result, Results, RequestHeader}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Implements the cross-origin resource sharing (CORS) filter.
  * @param strategy The strategy that determines whether or not a request is allowed.
  */
case class CorsFilter(var strategy: RequestHeader => Option[String]) extends Filter {

  private def isPreflight(request: RequestHeader) = {
    request.method.equalsIgnoreCase(OPTIONS) && request.headers.keys("Access-Control-Request-Method")
  }

  private def corsPreflight(request: RequestHeader) = strategy(request) match {
    case Some(header) => Results.Ok.withHeaders(corsHeaders(header, request): _*)
    case None => Results.MethodNotAllowed
  }

  private def corsHeaders(request: RequestHeader): Seq[(String, String)] = strategy(request) match {
    case Some(header) => corsHeaders(header, request)
    case None => Seq()
  }

  private def corsHeaders(origin: String, request: RequestHeader): Seq[(String, String)] = Seq(
    ACCESS_CONTROL_ALLOW_ORIGIN -> origin,
    ACCESS_CONTROL_EXPOSE_HEADERS -> s"$LOCATION, $AUTHORIZATION",
    ACCESS_CONTROL_ALLOW_METHODS -> request.headers.get(ACCESS_CONTROL_REQUEST_METHOD).getOrElse("*"),
    ACCESS_CONTROL_ALLOW_HEADERS -> request.headers.get(ACCESS_CONTROL_REQUEST_HEADERS).getOrElse("")
  )

  def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    if (isPreflight(request)) Future.successful(corsPreflight(request))
    else next(request).map(_.withHeaders(corsHeaders(request): _*))
  }
}
