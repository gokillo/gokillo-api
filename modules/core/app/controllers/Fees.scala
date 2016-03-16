/*#
  * @file Fees.scala
  * @begin 30-Mar-2015
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
import services.core.FeeDaoServiceComponent
import services.core.mongo.MongoFeeDaoComponent
import utils.common.Responses._
import models.core.Fee
import models.core.Fee._

@Api(
  value = "/core/fees",
  description = "Lookup project fees",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Fees extends Controller with Security {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors {}

  protected val feeService: FeeDaoServiceComponent#FeeDaoService = new FeeDaoServiceComponent
    with MongoFeeDaoComponent {
  }.daoService.asInstanceOf[FeeDaoServiceComponent#FeeDaoService]

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds the fee associated with a project",
    response = classOf[models.core.api.Fee])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Fee not found"),
    new ApiResponse(code = 500, message = "Error processing find fee request")))
  def find(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to find the fee for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("find") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj("projectId" -> projectId),
        None, None, 0, 1
      ).map { _.headOption match {
        case Some(fee) => Ok(success(Json.obj("fee" -> fee.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("fee for project", projectId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find fee for project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the fees",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list fees request")))
  def list(
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
  ) = Authorized("list") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj(),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list fees"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByCurrency",
    value = "Lists the fees by currency",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list fees by currency request")))
  def listByCurrency(
    @ApiParam(
      name = "currency",
      value = "The currency of the fees to list",
      allowableValues = "EUR,USD",
      required = true)
    @PathParam("currency")
    currency: String,

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
  ) = Authorized("listByCurrency") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj("currency" -> currency.toUpperCase),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list fees by currency"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listWithheld",
    value = "Lists all the withheld fees",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list withheld fees request")))
  def listWithheld(
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
  ) = Authorized("listWithheld") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj("withdrawalTime" -> JsNull),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list withheld fees"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listWithheldByCurrency",
    value = "Lists the withheld fees by currency",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list withheld fees by currency request")))
  def listWithheldByCurrency(
    @ApiParam(
      name = "currency",
      value = "The currency of the fees to list",
      allowableValues = "EUR,USD",
      required = true)
    @PathParam("currency")
    currency: String,

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
  ) = Authorized("listWithheldByCurrency") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj("withdrawalTime" -> JsNull, "currency" -> currency.toUpperCase),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list withheld fees by currency"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listWithdrawn",
    value = "Lists all the withdrawn fees",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list withdrawn fees request")))
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
      feeService.find(
        Json.obj("$gt" -> Json.obj("withdrawalTime" -> 0)),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list withdrawn fees"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listWithdrawnByCurrency",
    value = "Lists the withdrawn fees by currency",
    response = classOf[models.core.api.Fee],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No fees found"),
    new ApiResponse(code = 500, message = "Error processing list withdrawn fees by currency request")))
  def listWithdrawnByCurrency(
    @ApiParam(
      name = "currency",
      value = "The currency of the fees to list",
      allowableValues = "EUR,USD",
      required = true)
    @PathParam("currency")
    currency: String,

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
  ) = Authorized("listWithdrawnByCurrency") { token =>
    Action.async { implicit request =>
      feeService.find(
        Json.obj("$gt" -> Json.obj("withdrawalTime" -> 0), "currency" -> currency.toUpperCase),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { fees =>
        if (fees.isEmpty) errors.toResult(CommonErrors.EmptyList("fees"), None)
        else Ok(success(Json.obj("fees" -> Json.toJson(fees))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list withdrawn fees by currency"))
      }
    }
  }
}
