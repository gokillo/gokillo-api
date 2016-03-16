/*#
  * @file Rates.scala
  * @begin 4-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.pay

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors}
import services.auth.AuthErrors
import services.pay._
import utils.common.Responses._
import models.pay.RateType
import models.pay.RateType._
import models.auth.TokenType.{Authorization => Auth, Browse}

@Api(
  value = "/pay/rates",
  description = "Lookup exchange rates",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Rates extends Controller with Security {

  protected val errors = new CommonErrors with AuthErrors with PayErrors {}

  @ApiOperation(
    httpMethod = "GET",
    nickname = "current",
    value = "Gets the current exchange rates against a given reference currency",
    response = classOf[Map[String, Double]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid reference currency or rates not available"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing get exchange rates request")))
  def current(
    @ApiParam(
      name = "refCurrency",
      value = "The reference currency",
      required = true)
    @PathParam("refCurrency")
    refCurrency: String,
    @ApiParam(
      name = "rateType",
      value = "The type of the rates to get",
      allowableValues = "ask,bid,last,high,low",
      required = true)
    @PathParam("rateType")
    rateType: String
  ) = Authorized("current", Auth, Browse) { token =>
    Action.async { implicit request =>
      val _refCurrency = refCurrency.toUpperCase
      PayGateway.rates(_refCurrency, RateType(rateType)).map { rates =>
        Ok(success(Json.obj("rates" -> Json.toJson(rates))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not get exchange rates"))
      }
    }
  }
}
