/*#
  * @file Faqs.scala
  * @begin 16-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
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
import services.common.{CommonErrors, DaoErrors, FsmErrors}
import services.auth.AuthErrors
import services.core.projects.ProjectFsm
import services.core.projects.ProjectFsm._
import services.core.ProjectUniverse._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.auth.TokenType.{Browse, Authorization => Auth}
import models.core.Faq
import models.core.Faq._

@Api(value = "/core/projects")
trait Faqs extends Controller with Security {

  protected val errors: CommonErrors with DaoErrors with FsmErrors with AuthErrors

  @ApiOperation(
    httpMethod = "POST",
    nickname = "createFaq",
    value = "Creates a new frequently asked question for an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid faq data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Faq data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing create faq request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "faq",
    value = "The faq data",
    required = true,
    dataType = "models.core.api.Faq",
    paramType = "body")))
  def createFaq(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to create the faq for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("createFaq") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Faq](faqFormat(Some(false))).fold(
        valid = { faq =>
          Future(Open.withSecurity(token)).flatMap {
            _ ! CreateFaq(projectId, faq)
          }.map { r =>
            // r._1: one of the ProjectFsm values
            // r._2: the zero-based index of the added faq
            Logger.debug(s"added faq ${r._2} to project $projectId")
            Created(success(Json.obj("state" -> r._1))).withHeaders(
              LOCATION -> s"$requestUri/${r._2}"
            )
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create faq for project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create faq request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateFaq",
    value = "Updates a faq of a open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or faq not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update faq request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "faq",
    value = "The update data",
    required = true,
    dataType = "models.core.api.Faq",
    paramType = "body")))
  def updateFaq(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to update the faq for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the faq",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("updateFaq") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Faq](faqFormat(Some(true))).fold(
        valid = { update =>
          Future(Open.withSecurity(token)).flatMap {
            _ ! UpdateFaq(projectId, index, update)
          }.map { state =>
            Logger.debug(s"updated faq $index of project $projectId")
            Ok(success(Json.obj("state" -> state)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update faq $index of project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update faq request")))
        }
      )
   }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteFaq",
    value = "Deletes a faq of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or faq not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete faq request")))
  def deleteFaq(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to delete the faq for",
      required = true)
    @PathParam("projectId")
    projectId: String,
    
    @ApiParam(
      name = "index",
      value = "The zero-based index of the faq",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("deleteFaq") { token =>
    Action.async { implicit request =>
      Future(Open.withSecurity(token)).flatMap {
        _ ! DeleteFaq(projectId, index)
      }.map { state =>
        Logger.debug(s"deleted faq $index of project $projectId")
        Ok(success(Json.obj("state" -> state)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete faq $index of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findFaq",
    value = "Finds a faq of a project in a given state",
    response = classOf[models.core.api.Faq])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or faq not found"),
    new ApiResponse(code = 500, message = "Error processing find faq request")))
  def findFaq(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to find the faq for",
      required = true)
    @PathParam("projectId")
    projectId: String,
    
    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,
  
    @ApiParam(
      name = "index",
      value = "The zero-based index of the faq",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("findFaq", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! FindFaq(projectId, index)
      }.map {
        case Some(faq) => Ok(success(Json.obj("faq" -> faq.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("faq", index.toString, "project", projectId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find faq $index of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listFaqs",
    value = "Lists the faqs of a project in a given state",
    response = classOf[models.core.api.Faq],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 500, message = "Error processing list faqs request")))
  def listFaqs(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the faqs for",
      required = true)
    @PathParam("projectId")
    projectId: String,
  
    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("listFaqs", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListFaqs(projectId)
      }.map { faqs =>
        if (faqs.isEmpty) errors.toResult(CommonErrors.EmptyList("faqs", "project", projectId), None)
        else Ok(success(Json.obj("faqs" -> Json.toJson(faqs))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list faqs of project $projectId"))
      }
    }
  }
}
