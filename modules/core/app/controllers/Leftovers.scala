/*#
  * @file Leftovers.scala
  * @begin 7-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.core

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
import services.core.LeftoverDaoServiceComponent
import services.core.mongo.MongoLeftoverDaoComponent
import services.pay.{PayErrors, PayGateway}
import utils.common.env._
import utils.common.Responses._
import utils.common.typeExtensions._
import models.core.Leftover
import models.core.Leftover._

@Api(
  value = "/core/leftovers",
  description = "Lookup and manage leftovers",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Leftovers extends Controller with Security {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors with PayErrors {}

  protected val leftoverService: LeftoverDaoServiceComponent#LeftoverDaoService = new LeftoverDaoServiceComponent
    with MongoLeftoverDaoComponent {
  }.daoService.asInstanceOf[LeftoverDaoServiceComponent#LeftoverDaoService]

  @ApiOperation(
    httpMethod = "POST",
    nickname = "withdraw",
    value = "Withdraws the current leftover",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid coin address"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Leftover not found"),
    new ApiResponse(code = 500, message = "Error processing withdraw leftover request")))
  def withdraw(
    @ApiParam(
      name = "coinAddress",
      value = "The recipient coin address",
      required = true)
    @PathParam("coinAddress")
    coinAddress: String
  ) = Authorized("withdraw") { token =>
    Action.async { implicit request =>
      coinAddress.isCoinAddress match {
        case true => leftoverService.reset.flatMap {
          case Some(leftover) => PayGateway.sendCoinsLocal(token.account, leftover.amount, coinAddress).map { order =>
            Logger.debug(s"sent ${leftover.amount.currency} ${leftover.amount.value} to coin address $coinAddress")
            Logger.debug(s"created send order ${order.id.get}")
            Created(success).withHeaders(
              LOCATION -> s"""${localhost.toString("/pay/orders/" + order.id.get)}"""
            )
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not withdraw current leftover and send it to coin address $coinAddress"))
          }
          case _ => Future.successful(errors.toResult(CommonErrors.NotFound("leftover"), None))
        }
        case false => Future.successful(errors.toResult(PayErrors.InvalidCoinAddress(coinAddress), None))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findCurrent",
    value = "finds the curren leftover",
    response = classOf[models.core.api.Leftover])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Leftover not found"),
    new ApiResponse(code = 500, message = "Error processing find current leftover request")))
  def findCurrent = Authorized("findCurrent") { token =>
    Action.async { implicit request =>
      leftoverService.findCurrent.map {
        case Some(leftover) => Ok(success(Json.obj("leftover" -> leftover.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("leftover"), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not find current leftover"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listWithdrawn",
    value = "Lists the leftovers withdrawn so far",
    response = classOf[models.core.api.Leftover],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No leftovers found"),
    new ApiResponse(code = 500, message = "Error processing list withdrawn leftovers request")))
  def listWithdrawn(
    @ApiParam(
      name = "page",
      value = "The page to retrieve (0-based)",
      required = true)
    @PathParam("page")
    page: Int,

    @ApiParam(
      name = "perPage",
      value = "The number of results per page",
      required = true)
    @PathParam("perPage")
    perPage: Int
  ) = Authorized("listWithdrawn") { token =>
    Action.async { implicit request =>
      leftoverService.findWithdrawn(page, perPage).map { leftovers =>
        if (leftovers.isEmpty) errors.toResult(CommonErrors.EmptyList("leftovers"), None)
        else Ok(success(Json.obj("leftovers" -> Json.toJson(leftovers))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list withdrawn leftovers"))
      }
    }
  }
}
