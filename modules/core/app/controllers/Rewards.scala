/*#
  * @file Rewards.scala
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
import controllers.common.FsController
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
import models.core.Reward

@Api(value = "/core/projects")
trait Rewards extends Controller with Security with FsController {

  protected val errors: CommonErrors with DaoErrors with FsmErrors with AuthErrors

  @ApiOperation(
    httpMethod = "POST",
    nickname = "createReward",
    value = "Creates a new reward for an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid reward data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Reward data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing create reward request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "reward",
    value = "The reward data",
    required = true,
    dataType = "models.core.api.Reward",
    paramType = "body")))
  def createReward(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to create the reward for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("createReward") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Reward].fold(
        valid = { reward =>
          Future(Open.withSecurity(token)).flatMap {
            _ ! CreateReward(projectId, reward)
          }.map { r =>
            // r._1: one of the ProjectFsm values
            // r._2: the zero-based index of the added reward
            Logger.debug(s"added reward ${r._2} to project $projectId")
            Created(success(Json.obj("state" -> r._1))).withHeaders(
              LOCATION -> s"$requestUri/${r._2}"
            )
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create reward for project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create reward request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateReward",
    value = "Updates a reward of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update reward request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "reward",
    value = "The update data",
    required = true,
    dataType = "models.core.api.Reward",
    paramType = "body")))
  def updateReward(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to update the reward for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("updateReward") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Reward].fold(
        valid = { update =>
          Future(Open.withSecurity(token)).flatMap {
            _ ! UpdateReward(projectId, index, update)
          }.map { state =>
            Logger.debug(s"updated reward $index of project $projectId")
            Ok(success(Json.obj("state" -> state)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update reward $index of project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update reward request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteReward",
    value = "Deletes a reward of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete reward request")))
  def deleteReward(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to delete the reward for",
      required = true)
    @PathParam("projectId")
    projectId: String,
    
    @ApiParam(
      name = "index",
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("deleteReward") { token =>
    Action.async { implicit request =>
      Future(Open.withSecurity(token)).flatMap {
        _ ! DeleteReward(projectId, index)
      }.map { state =>
        Logger.debug(s"deleted reward $index of project $projectId")
        Ok(success(Json.obj("state" -> state)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete reward $index of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findReward",
    value = "Finds a reward of a project in a given state",
    response = classOf[models.core.api.Reward])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 500, message = "Error processing find reward request")))
  def findReward(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to find the reward for",
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
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("findReward", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! FindReward(projectId, index)
      }.map {
        case Some(reward) => Ok(success(Json.obj("reward" -> reward.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("reward", index.toString, "project", projectId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find reward $index of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findRewardById",
    value = "Finds a reward of a project in a given state",
    response = classOf[models.core.api.Reward])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project or reward id, or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 500, message = "Error processing find reward by id request")))
  def findRewardById(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to find the reward for",
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
      name = "rewardId",
      value = "The id of the reward to find",
      required = true)
    @PathParam("rewardId")
    rewardId: String
  ) = Authorized("findRewardById", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! FindRewardById(projectId, rewardId)
      }.map {
        case Some(reward) => Ok(success(Json.obj("reward" -> reward.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("reward", rewardId, "project", projectId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find reward $rewardId of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listRewards",
    value = "Lists the rewards of a project in a given state",
    response = classOf[models.core.api.Reward],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 500, message = "Error processing list rewards request")))
  def listRewards(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the rewards for",
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
  ) = Authorized("listRewards", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListRewards(projectId)
      }.map { rewards =>
        if (rewards.isEmpty) errors.toResult(CommonErrors.EmptyList("rewards", "project", projectId), None)
        else Ok(success(Json.obj("rewards" -> Json.toJson(rewards))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list rewards of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "saveRewardMedia",
    value = "Saves the media of a reward of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing save reward media request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "media",
    value = "The reward media",
    required = true,
    dataType = "file",
    paramType = "body")))
  def saveRewardMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to save the reward media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("saveRewardMedia") { token =>
    Action.async(fsBodyParser) { implicit request =>
      val result = for {
        media <- request.body.files.head.ref
        update <- Open.withSecurity(token) ! AddRewardMedia(projectId, index, media)
      } yield update

      result.map { state =>
        Logger.debug(s"saved reward media $index for project $projectId")
        Created(success(Json.obj("state" -> state))).withHeaders(
          LOCATION -> requestUri
        )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not save reward media $index for project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteRewardMedia",
    value = "Deletes the media of a reward of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or reward not found"),
    new ApiResponse(code = 500, message = "Error processing delete reward media request")))
  def deleteRewardMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to delete the reward media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("deleteRewardMedia") { token =>
    Action.async { implicit request =>
      Future(Open.withSecurity(token)).flatMap {
        _ ! DeleteRewardMedia(projectId, index)
      }.map { state =>
        Logger.debug(s"deleted reward media $index of project $projectId")
        Ok(success(Json.obj("state" -> state)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete reward media $index of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getRewardMedia",
    value = "Gets the media of a reward of a project in a given state",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project, reward, or media not found"),
    new ApiResponse(code = 500, message = "Error processing get reward media request")))
  def getRewardMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to get the reward media for",
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
      value = "The zero-based index of the reward",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("getRewardMedia", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! FindRewardMedia(projectId, index)
      }.flatMap {
        case Some(media) => serveFile(media)
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound(s"reward media $index of project", projectId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get reward media $index of project $projectId"))
      }
    }
  }
}
