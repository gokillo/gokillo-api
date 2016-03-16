/*#
  * @file CorsSupport.scala
  * @begin 28-Dec-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package filters

import scala.concurrent.Future
import play.api.GlobalSettings
import play.api.http.HttpVerbs._
import play.api.mvc._

/**
  * Adds support for cross-origin resource sharing (CORS).
  */
trait CorsSupport extends GlobalSettings {

  private lazy val corsFilter = CorsFilter(CorsStrategy.NoOne)

  private def preflight(request: RequestHeader) = Some(request.method)
    .filter(_.equalsIgnoreCase(OPTIONS))
    .map(_ => Future.successful(Results.Ok))

  def corsStrategy = corsFilter.strategy
  def corsStrategy_= (strategy: CorsStrategy) = corsFilter.strategy = strategy

  /**
    * Adds [[CorsFilter]] to application's filter chain.
    */
  override def doFilter(action: EssentialAction): EssentialAction = {
    Filters(super.doFilter(action), corsFilter)
  }

  /**
    * Called when no action was found to serve a request.
    *
    * @param request  The HTTP request header.
    * @return         The result to send to the client.
    * @note           Enables preflight requests even if not specified in the routes file.
    */
  override def onHandlerNotFound(request: RequestHeader): Future[Result] = {
    preflight(request).getOrElse(super.onHandlerNotFound(request))
  }
}
