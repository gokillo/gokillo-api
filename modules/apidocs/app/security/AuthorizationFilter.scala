/*#
  * @file AuthorizationFilter.scala
  * @begin 22-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package security.apidocs

import play.api.Logger
import com.wordnik.swagger.model._
import com.wordnik.swagger.core.filter.SwaggerSpecFilter

/**
  * Provides functionality for securing API access.
  */
class AuthorizationFilter extends SwaggerSpecFilter {

  /**
    * Returns a Boolean value indicating whether or not the specified
    * operation is allowed.
    *
    * @param operation  The operation to perform.
    * @param api        The API description.
    * @param params     The request query string.
    * @param cookies    The request cookies.
    * @param headers    The request headers.
    * @return           `true` if `operation` is allowed; otherwise, `false`.
    */
  def isOperationAllowed(
    operation: Operation,
    api: ApiDescription,
    params: java.util.Map[String, java.util.List[String]],
    cookies: java.util.Map[String, String],
    headers: java.util.Map[String, java.util.List[String]]): Boolean = {

    // not implemented yet
    Logger("swagger").debug(s"authorized: true - method: ${operation.method} - path: ${api.path}")
    true
  }

  /**
    * Returns a Boolean value indicating whether or not the specified
    * parameter is allowed for the specified operation.
    *
    * @param parameter  The operation parameter.
    * @param operation  The operation to perform.
    * @param api        The API description.
    * @param params     The request query string.
    * @param cookies    The request cookies.
    * @param headers    The request headers.
    * @return           `true` if `parameter` is allowed in `operation`;
    *                   otherwise, `false`.
    */
  def isParamAllowed(
    parameter: Parameter,
    operation: Operation,
    api: ApiDescription,
    params: java.util.Map[String, java.util.List[String]],
    cookies: java.util.Map[String, String],
    headers: java.util.Map[String, java.util.List[String]]): Boolean = {

    // not implemented yet
    true
  }
}
