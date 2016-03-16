/*#
  * @file Emails.scala
  * @begin 15-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.messaging

import scala.concurrent.Future
import scala.util.{Success, Failure}
import java.nio.charset.{StandardCharsets => SC}
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api.mvc.{Action, Controller}
import play.twirl.api.Html
import play.utils.UriEncoding
import brix.util.GZip
import controllers.auth.Security
import services.common.CommonErrors
import services.auth.AuthErrors
import utils.common.Responses._
import models.auth.TokenType.Browse

@Api(
  value = "/messaging/emails",
  description = "Render zipped emails",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Emails extends Controller with Security {

  protected val errors = new CommonErrors with AuthErrors {}

  @ApiOperation(
    httpMethod = "GET",
    nickname = "render",
    value = "Renders a zipped email",
    response = classOf[Html])
  @ApiResponses(Array(
    new ApiResponse(code = 500, message = "Error processing render email request")))
  def render(
    @ApiParam(
      name = "zipped",
      value = "The zipped email to render",
      required = true)
    @PathParam("zipped")
    zipped: String
  ) = Authorized("render", Browse) { token =>
    Action.async { implicit request =>
      Future.successful {
        GZip.inflate(UriEncoding.decodePath(zipped, SC.US_ASCII.name)) match {
          case Success(inflated) => Ok(Html(inflated))
          case Failure(e) => errors.toResult(e, Some(s"error processing render email request"))
        }
      }
    }
  }
}
