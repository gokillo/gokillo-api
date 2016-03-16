/*#
  * @file RequestHelper.scala
  * @begin 23-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import play.api.mvc.RequestHeader
import utils.common.env._

/**
  * Provides functionality for extending request information.
  */
object RequestHelper {

  /** Gets the request URI. */
  def requestUri(implicit request: RequestHeader) = s"${localhost.toString(request.path)}"

  /** Gets the request URI, including HTTP method. */
  def requestUriWithMethod(implicit request: RequestHeader) = s"${request.method} ${requestUri}"
}
