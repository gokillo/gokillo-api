/*#
  * @file Projects.scala
  * @begin 16-Jun-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.core

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import org.joda.time.{DateTime, DateTimeZone, Period}
import org.joda.time.format.PeriodFormatterBuilder
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.iteratee.Done
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.common.FsController
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors, FsmErrors}
import services.auth.AuthErrors
import services.core.projects.ProjectFsm
import services.core.projects.ProjectFsm._
import services.core.ProjectUniverse._
import services.pay.PayErrors
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import utils.common.{parse => parseParam}
import models.common.{Comment, MetaFile, RefId}
import models.common.Comment._
import models.auth.Token
import models.auth.TokenType.{Browse, Authorization => Auth}
import models.core.{Project, FundingInfo, ShippingInfo, CoinAddressChange}
import models.core.Project._
import models.core.FundingInfo._
import models.core.ShippingInfo._
import models.core.CoinAddressChange._
import models.pay.{Coin, PaymentRequest}
import models.pay.Coin._
import models.pay.PaymentRequest._

@Api(
  value = "/core/projects",
  description = "Create projects and manage their workflows",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Projects extends Controller
  with Security
  with FsController
  with Rewards
  with Faqs {

  protected val errors = new CommonErrors with DaoErrors with FsmErrors with AuthErrors with PayErrors {}

  /**
    * Creates an action that authorizes the specified operation only if issued
    * by the owner of the specified account.
    *
    * @param accountId  The identifier of the account whose owner is allowed to issue `operation`.
    * @param operation  The name of the operation to authorize.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  protected def Authorized(accountId: String, operation: String)(action: Token => EssentialAction): EssentialAction = Authorized(operation) { token =>
    EssentialAction { implicit request =>
      if (accountId == token.account) action(token)(request)
      else Done(errors.toResult(
        AuthErrors.NotAuthorized("operation", operation, "account", token.account, s"accoun $accountId is not the owner of project ${token.account}"),
        Some(s"request $requestUriWithMethod not authorized")
      ))
    }   
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "create",
    value = "Creates a new project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "Project data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing create project request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "project",
    value = "The project data",
    required = true,
    dataType = "models.core.api.Project",
    paramType = "body")))
  def create = Authorized("create") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Project](projectFormat(Some(false))).fold(
        valid = { project =>
          project.accountId = Some(token.account)
          (New ! Save(project)).map { r =>
            // r._1: one of the ProjectFsm values
            // r._2: new instance of the Project class
            Logger.debug(s"created project ${r._2.id.get} on account ${r._2.accountId.get}")
            Created(success(Json.obj("state" -> r._1))).withHeaders(
              LOCATION -> s"$requestUri/${r._2.id.get}"
            )
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not create project"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create project request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "update",
    value = "Updates an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update project request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "project",
    value = "The update data",
    required = true,
    dataType = "models.core.api.Project",
    paramType = "body")))
  def update(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to update",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("update") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Project](projectFormat(Some(true))).fold(
        valid = { update =>
          Future(Open.withSecurity(token)).flatMap {
            _ ! Update(projectId, update)
          }.map { state =>
            Logger.debug(s"updated project $projectId in state $state")
            Ok(success(Json.obj("state" -> state)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update project request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "delete",
    value = "Deletes a project in a given state",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete project request")))
  def delete(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to delete",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,rejected,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("delete") { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! Delete(projectId)
      }.map { newState =>
        Logger.debug(s"deleted project $projectId in state $state")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds a project in a given state",
    response = classOf[models.core.api.Project])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 500, message = "Error processing find project request")))
  def find(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to find",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("find", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! Find(projectId)
      }.map {
        case Some(project) => Ok(success(Json.obj("project" -> project.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("project", projectId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the projects in a given state",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects request")))
  def list(
    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

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
  ) = Authorized("list", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! List(None, None, None, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list projects"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByName",
    value = "Lists all the projects in a given state by name",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects by name request")))
  def listByName(
    @ApiParam(
      name = "state",
      value = "The state of the projects to list",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "name",
      value = "The full or partial name of the projects to list",
      required = true)
    @PathParam("name")
    name: String,

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
  ) = Authorized("listByName", Auth, Browse) { token =>
    Action.async { implicit request =>
      val selector = Some(Json.obj("$like" -> Json.obj("name" -> name)))

      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! List(selector, None, None, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list projects by name"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByCategory",
    value = "Lists all the projects in a given state by category",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects by category request")))
  def listByCategory(
    @ApiParam(
      name = "state",
      value = "The state of the projects to list",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "category",
      value = "The category of the projects to list",
      required = true)
    @PathParam("category")
    category: String,

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
  ) = Authorized("listByCategory", Auth, Browse) { token =>
    Action.async { implicit request =>
      val selector = Some(Json.obj("categories" -> Json.obj("$in" -> Json.arr(category))))
      val sort = Some(Json.obj("$asc" -> Json.arr("categories")))

      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! List(selector, None, sort, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list projects by category"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByLocation",
    value = "Lists all the projects in a given state by location",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects by location request")))
  def listByLocation(
    @ApiParam(
      name = "state",
      value = "The state of the projects to list",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "location",
      value = "The location of the projects to list",
      required = true)
    @PathParam("location")
    location: String,

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
  ) = Authorized("listByLocation", Auth, Browse) { token =>
    Action.async { implicit request =>
      val selector = Some(Json.obj("$like" -> Json.obj("location" -> location)))
      val sort = Some(Json.obj("$asc" -> Json.arr("location")))

      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! List(selector, None, sort, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list projects by location"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByAccount",
    value = "Lists all the projects in a given state by account",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state or account id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects by account request")))
  def listByAccount(
    @ApiParam(
      name = "state",
      value = "The state of the projects to list",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "accountId",
      value = "The id of the account to list the projects for",
      required = true)
    @PathParam("accountId")
    accountId: String,

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
  ) = Authorized("listByAccount") { token =>
    Action.async { implicit request =>
      val selector = Some(Json.obj("accountId" -> accountId))
      val sort = Some(Json.obj("$asc" -> Json.arr("name")))

      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! List(selector, None, sort, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list projects in state $state for account $accountId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listAllByAccount",
    value = "Lists all the projects associated with an account regardless of their state",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid account id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list projects by account request")))
  def listAllByAccount(
    @ApiParam(
      name = "accountId",
      value = "The id of the account to list the projects for",
      required = true)
    @PathParam("accountId")
    accountId: String
  ) = Authorized("listAllByAccount") { token =>
    Action.async { implicit request =>
      val selector = Json.obj("accountId" -> accountId)
      val projection = Some(Json.obj("$exclude" -> Json.arr("rewards", "faqs", "history", "_version")))
      val sort = Some(Json.obj("$asc" -> Json.arr("name")))

      daoService.find(selector, projection, sort, 0, Int.MaxValue) zip
      wipService.find(selector, projection, sort, 0, Int.MaxValue) map { case (dao, wip) =>
        if (dao.isEmpty && wip.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(dao ++ wip))))
      } recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list projects for account $accountId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listEnding",
    value = "Lists the projects whose fundraising period ends within a given lapse",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list ending request")))
  def listEnding(
    @ApiParam(
      name = "lapse",
      value = "The lapse (in milliseconds) within the fundraising period the projects to list ends",
      required = true)
    @PathParam("lapse")
    lapse: Int,

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
  ) = Authorized("listEnding", Auth, Browse) { token =>
    Action.async { implicit request =>
      val endTime = DateTime.now(DateTimeZone.UTC).plusMillis(lapse).getMillis
      val selector = Some(Json.obj("$lte" -> Json.obj("fundingInfo.endTime" -> endTime)))
      val sort = Some(Json.obj("$asc" -> Json.arr("fundingInfo.endTime")))

      Future(Published.withSecurity(token)).flatMap {
        _ ! List(selector, None, sort, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        val formatter = new PeriodFormatterBuilder()
          .appendYears.appendSuffix(" years, ")
          .appendMonths.appendSuffix(" months, ")
          .appendWeeks.appendSuffix(" weeks, ")
          .appendDays.appendSuffix(" days, ")
          .appendHours.appendSuffix(" hours, ")
          .appendMinutes.appendSuffix(" minutes, ")
          .appendSeconds.appendSuffix(" seconds")
          .printZeroNever
          .toFormatter
        val formatted = formatter.print(new Period(lapse))
        errors.toResult(e, Some(s"could not list projects whose fundraising period ends within $formatted"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listLatest",
    value = "Lists the projects up to a given age",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list latest request")))
  def listLatest(
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
  ) = Authorized("listLatest", Auth, Browse) { token =>
    Action.async { implicit request =>
      val sort = Some(Json.obj("$desc" -> Json.arr("fundingInfo.startTime")))

      Future(Published.withSecurity(token)).flatMap {
        _ ! List(None, None, sort, page, perPage)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list latest projects"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listRandom",
    value = "Lists projects randomly",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list random request")))
  def listRandom(
    @ApiParam(
      name = "resNum",
      value = "The number of results to retrieve",
      required = true)
    @PathParam("resNum")
    resNum: Int
  ) = Authorized("listRandom", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(Published.withSecurity(token)).flatMap {
        _ ! ListRandom(resNum)
      }.map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list projects randomly"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "count",
    value = "Counts all the projects in a give state",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing count projects request")))
  def count(
    @ApiParam(
      name = "state",
      value = "The state of the projects to count",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("count", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! Count()
      }.map { n =>
        Ok(success(Json.obj("projectCount" -> n)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not count projects"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "countBackers",
    value = "Counts all the backers of a project in a give state",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing count backers request")))
  def countBackers(
    @ApiParam(
      name = "state",
      value = "The state of the project to count the backers for",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "projectId",
      value = "The id of the project to count the backers for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("countBackers", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! CountBackers(projectId)
      }.map { n =>
        Ok(success(Json.obj("backerCount" -> n)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not count backers for project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listBackers",
    value = "Lists the backers of a project in a given state",
    response = classOf[models.auth.api.User],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state or project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or backers not found"),
    new ApiResponse(code = 500, message = "Error processing list backers request")))
  def listBackers(
    @ApiParam(
      name = "state",
      value = "The state of the project to list the backers for",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the backers for",
      required = true)
    @PathParam("projectId")
    projectId: String,

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
  ) = Authorized("listBackers", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListBackers(projectId, page, perPage)
      }.map { backers =>
        if (backers.isEmpty) errors.toResult(CommonErrors.EmptyList("backers"), None)
        else Ok(success(Json.obj("backers" -> Json.toJson(backers))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list backers of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listPledges",
    value = "Lists the pledges of a project in a given state",
    response = classOf[models.core.api.Pledge],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state or project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or pledges not found"),
    new ApiResponse(code = 500, message = "Error processing list pledges request")))
  def listPledges(
    @ApiParam(
      name = "state",
      value = "The state of the project to list the pledges for",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the pledges for",
      required = true)
    @PathParam("projectId")
    projectId: String,

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
  ) = Authorized("listPledges", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListPledges(projectId, page, perPage)
      }.map { pledges =>
        if (pledges.isEmpty) errors.toResult(CommonErrors.EmptyList("pledges"), None)
        else Ok(success(Json.obj("pledges" -> Json.toJson(pledges))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list pledges of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listPledgesByState",
    value = "Lists the pledges of a project in a given state by state",
    response = classOf[models.core.api.Pledge],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state or project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or pledges not found"),
    new ApiResponse(code = 500, message = "Error processing list pledges by state request")))
  def listPledgesByState(
    @ApiParam(
      name = "state",
      value = "The state of the project to list the pledges for",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the pledges for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "pledgeState",
      value = "The state of the pledges to list",
      allowableValues = "settled,granted,revoked,cashedIn,rewarded,awaitingRefundThreshold,awaitingRefund,refunded,unclaimed",
      required = true)
    @PathParam("pledgeState")
    pledgeState: String,

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
  ) = Authorized("listPledgesByState", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListPledgesByState(projectId, pledgeState, page, perPage)
      }.map { pledges =>
        if (pledges.isEmpty) errors.toResult(CommonErrors.EmptyList("pledges"), None)
        else Ok(success(Json.obj("pledges" -> Json.toJson(pledges))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list pledges of project $projectId by state"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "changeCoinAddress",
    value = "Changes the coin address associated with a project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or current coin address does not match"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Coin address data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing change coin address request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "coinAddressChange",
    value = "The current coin address and the new coin address",
    required = true,
    dataType = "models.core.api.CoinAddressChange",
    paramType = "body")))
  def changeCoinAddress(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to change the coin address for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("changeCoinAddress") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[CoinAddressChange].fold(
        valid = { coinAddressChange =>
          val currentCoinAddress = coinAddressChange.currentCoinAddress
          val newCoinAddress = coinAddressChange.newCoinAddress

          Future(Open.withSecurity(token)).flatMap {
            _ ! ChangeCoinAddress(projectId, currentCoinAddress, newCoinAddress)
          }.map { _ =>
            Logger.debug(s"coin address of project $projectId changed from $currentCoinAddress to $newCoinAddress")
            Ok(success)
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not change coin address of project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing change coin address request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "submit",
    value = "Submits a project for auditing",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing submit project for auditing request")))
  def submit(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to submit for auditing",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("submit") { token =>
    Action.async { implicit request =>
      (Open.withSecurity(token) ! Submit(projectId)).map { newState =>
        Logger.debug(s"state of project $projectId changed from $Open to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not submit project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "acquireForAudit",
    value = "Acquires a project for auditing",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing acquire project for auditing request")))
  def acquireForAudit(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to acquire for auditing",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("acquireForAudit") { token =>
    Action.async { implicit request =>
      (Submitted ! AcquireForAudit(projectId)).map { newState =>
        Logger.debug(s"state of project $projectId changed from $Submitted to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not acquire project $projectId for auditing"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "publish",
    value = "Publishes a project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing publish project request")))
  def publish(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to publish",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("publish") { token =>
    Action.async { implicit request =>
      (Audit ! Publish(projectId)).map { newState =>
        Logger.debug(s"state of project $projectId changed from $Audit to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not publish project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "reject",
    value = "Rejects a project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing reject project request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "comment",
    value = "The reason the project was rejected",
    required = true,
    dataType = "models.common.api.Comment",
    paramType = "body")))
  def reject(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to reject",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("reject") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Comment].fold(
        valid = { comment =>
          (Audit ! Reject(projectId, comment.text)).map { newState =>
            Logger.debug(s"state of project $projectId changed from $Audit to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not reject project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing reject project request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "edit",
    value = "Reopens a project in a given state for editing",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing edit project request")))
  def edit(
    @ApiParam(
      name = "state",
      value = "The state of the project to edit",
      allowableValues = "submitted,published,rejected,closed",
      required = true)
    @PathParam("state")
    state: String,

    @ApiParam(
      name = "projectId",
      value = "The id of the project to edit",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("edit") { token =>
    Action.async { implicit request =>
      (ProjectFsm(state).withSecurity(token) ! Edit(projectId)).map { newState =>
        Logger.debug(s"state of project $projectId changed from $Published to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not edit project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "relist",
    value = "Relists a project for funding",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing relist project request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "fundingInfo",
    value = "The new funding conditions of the project",
    required = true,
    dataType = "models.core.api.FundingInfo",
    paramType = "body")))
  def relist(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to relist",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("relist") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[FundingInfo](fundingInfoFormat(Some(false))).fold(
        valid = { fundingInfo =>
          (Closed ! Relist(projectId, fundingInfo)).map { newState =>
            Logger.debug(s"state of project $projectId changed from $Closed to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not relist project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing relist project request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "refundPledges",
    value = "Refunds the pledges towards a project that did not reach its funding target",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing refund pledges request")))
  def refundPledges(
    @ApiParam(
      name = "projectId",
      value = "The id of the project that holds the pledges to refund",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "coinAddress",
      value = "The recipient coin address",
      required = true)
    @PathParam("coinAddress")
    coinAddress: String

  ) = Authorized("refundPledges") { token =>
    Action.async { implicit request =>
      (Closed.withSecurity(token) ! RefundPledges(projectId, coinAddress)).map { _ =>
        Logger.debug(s"account ${token.account} applied for refund of its pledges towards project $projectId")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not refund pledges towards project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setShippingInfo",
    value = "Sets shipping info for the delivery of physical rewards",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing set shipping info request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "shippingInfo",
    value = "Shipping info of the user associated with the request",
    required = true,
    dataType = "models.core.api.ShippingInfo",
    paramType = "body")))
  def setShippingInfo(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to receive physical rewards for",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("setShippingInfo") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[ShippingInfo].fold(
        valid = { shippingInfo =>
          (Succeeded ! SetShippingInfo(projectId, shippingInfo)).map { _ =>
            Logger.debug(s"set shipping info of user ${token.username} to receive physical rewards for project $projectId")
            Ok(success)
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not set shipping info of user ${token.username} to receive physical rewards for project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing set shipping info request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "grantFunding",
    value = "Grants funding to a project before the due time",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing grant funding request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "comment",
    value = "The reason the funding was granted before the due time",
    required = true,
    dataType = "models.common.api.Comment",
    paramType = "body")))
  def grantFunding(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to grant funding to",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("grantFunding") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Comment].fold(
        valid = { comment =>
          (Published ! GrantFunding(projectId, comment.text)).map { newState =>
            Logger.debug(s"state of project $projectId changed from $Published to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not grant funding to project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing grant funding request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "close",
    value = "Closes a project before the due time",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing close project request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "comment",
    value = "The reason the project was closed before the due time",
    required = true,
    dataType = "models.common.api.Comment",
    paramType = "body")))
  def close(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to close",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("close") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Comment].fold(
        valid = { comment =>
          (Published ! Close(projectId, comment.text)).map { newState =>
            Logger.debug(s"state of project $projectId changed from $Published to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not close project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing close project request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "pick",
    value = "Adds a project to the list of preferred projects",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing pick project request")))
  def pick(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to pick",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("pick") { token =>
    Action.async { implicit request =>
      (Published ! Pick(projectId)).map { _ =>
        Logger.debug(s"project $projectId picked successfully")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not pick project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unpick",
    value = "Removes a project from the list of preferred projects",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing unpick project request")))
  def unpick(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to unpick",
      required = true)
    @PathParam("projectId")
    projectId: String
  ) = Authorized("unpick") { token =>
    Action.async { implicit request =>
      (Published ! Unpick(projectId)).map { _ =>
        Logger.debug(s"project $projectId unpicked successfully")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not unpick project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listPicked",
    value = "Lists the preferred projects",
    response = classOf[models.core.api.Project],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No projects found"),
    new ApiResponse(code = 500, message = "Error processing list picked projects request")))
  def listPicked(
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
  ) = Authorized("listPicked", Auth, Browse) { token =>
    Action.async { implicit request =>
      val selector = Some(Json.obj("picked" -> true))

      (Published ! List(selector, None, None, page, perPage)).map { projects =>
        if (projects.isEmpty) errors.toResult(CommonErrors.EmptyList("projects"), None)
        else Ok(success(Json.obj("projects" -> Json.toJson(projects))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list picked projects"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "issuePaymentRequest",
    value = "Issues a payment requests for a given project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id, order id, or state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 422, message = "Message with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing issue payment request request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "amount",
    value = "The amount of the payment request",
    required = true,
    dataType = "models.pay.api.Coin",
    paramType = "body")))
  def issuePaymentRequest(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to issue the payment request for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "orderId",
      value = "The id of the order the payment request is associated with",
      required = false)
    @PathParam("orderId")
    orderId: String
  ) = Authorized("issuePaymentRequest") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Coin](coinFormat).fold(
        valid = { amount =>
          val paymentRequest = PaymentRequest(
            amount = amount,
            refId = RefId("projects", "id", projectId),
            orderId = if (orderId != null) Some(orderId) else None
          )

          (Published.withSecurity(token) ! IssuePaymentRequest(projectId, paymentRequest)).map { invoice =>
            Ok(success(Json.obj("invoice" -> invoice.asJson)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not issue payment request for project $projectId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing issue payment request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "fund",
    value = "Funds a project that reached its funding target",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing fund project request")))
  def fund(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to fund",
      required = true)
    @PathParam("projectId")
    projectId: String,
    @ApiParam(
      name = "coinAddress",
      value = "The recipient coin address",
      required = true)
    @PathParam("coinAddress")
    coinAddress: String
  ) = Authorized("fund") { token =>
    Action.async { implicit request =>
      Future(Succeeded.withSecurity(token)).flatMap {
        _ ! Fund(projectId, coinAddress)
      }.map { newState =>
        Logger.debug(s"state of project $projectId changed from $Succeeded to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not fund project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "rewardPledge",
    value = "Rewards a pledge to a given project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id, invalid pledge id, or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or pledge not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing reward pledge request")))
  def rewardPledge(
    @ApiParam(
      name = "projectId",
      value = "The id of the project the pledge is associated with",
      required = true)
    @PathParam("projectId")
    projectId: String,
    @ApiParam(
      name = "pledgeId",
      value = "The id of the pledge to reward",
      required = true)
    @PathParam("pledgeId")
    pledgeId: String
  ) = Authorized("rewardPledge") { token =>
    Action.async { implicit request =>
      Future(Funded.withSecurity(token)).flatMap {
        _ ! RewardPledge(projectId, pledgeId)
      }.map { newPledgeState =>
        import services.core.pledges.PledgeFsm._
        Logger.debug(s"state of pledge $pledgeId changed from $CashedIn to $newPledgeState")
        Ok(success(Json.obj("pledgeState" -> newPledgeState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not reward pledge $pledgeId of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "saveMedia",
    value = "Saves a media of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing save media request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "media",
    value = "The media to save",
    required = true,
    dataType = "file",
    paramType = "body")))
  def saveMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to save the media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "cover",
      value = "Whether the media is the cover of the project, default to false",
      allowableValues = "0,1",
      required = false)
    @PathParam("cover")
    cover: Int
  ) = Authorized("saveMedia") { token =>
    Action.async(fsBodyParser) { implicit request =>
      val isCover = if (cover != 0) Some(true) else None
      val result = for {
        media <- request.body.files.head.ref
        update <- Open.withSecurity(token) ! AddMedia(projectId, media, isCover)
      } yield update

      result.map { state =>
        Logger.debug(s"saved media for project $projectId")
        Created(success(Json.obj("state" -> state))).withHeaders(
          LOCATION -> requestUri
        )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not save media for project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteMedia",
    value = "Deletes a media of an open project",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid media id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Media not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete media request")))
  def deleteMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to delete the media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "mediaId",
      value = "The id of the media to delete",
      required = true)
    @PathParam("mediaId")
    mediaId: String
  ) = Authorized("deleteMedia") { token =>
    Action.async { implicit request =>
      Future(Open.withSecurity(token)).flatMap {
        _ ! DeleteMedia(projectId, mediaId)
      }.map { state =>
        Logger.debug(s"deleted media $mediaId of project $projectId")
        Ok(success(Json.obj("state" -> state)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete media of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getMedia",
    value = "Gets a media of a project in a given state",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id, invalid media id, or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Media not found"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 500, message = "Error processing get media request")))
  def getMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to get the media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "mediaId",
      value = "The id of the media to get",
      required = true)
    @PathParam("mediaId")
    mediaId: String,

    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("getMedia", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! FindMedia(projectId, mediaId)
      }.flatMap {
        case Some(media) => serveFile(media)
        case _ => Future.successful( errors.toResult(CommonErrors.NotFound(s"media $mediaId of project", projectId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get media of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listMedia",
    value = "Lists the media of a project in a given state",
    response = classOf[models.common.api.MetaFile],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project or media not found"),
    new ApiResponse(code = 500, message = "Error processing list media request")))
  def listMedia(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to list the media for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("listMedia", Auth, Browse) { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! ListMedia(projectId)
      }.map { media =>
        if (media.isEmpty) errors.toResult(CommonErrors.EmptyList("media", "project", projectId), None)
        else Ok(success(Json.obj("media" -> Json.toJson(media))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list media of project $projectId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getHistory",
    value = "Gets the history of a project in a given state",
    response = classOf[models.common.api.HistoryEvent],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid project id or invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Project not found"),
    new ApiResponse(code = 500, message = "Error processing get history request")))
  def getHistory(
    @ApiParam(
      name = "projectId",
      value = "The id of the project to get the history for",
      required = true)
    @PathParam("projectId")
    projectId: String,

    @ApiParam(
      name = "state",
      value = "The state of the project",
      allowableValues = "open,submitted,audit,published,awaitingOrdersCompletion,awaitingCoins,rejected,succeeded,funded,closed",
      required = true)
    @PathParam("state")
    state: String
  ) = Authorized("getHistory") { token =>
    Action.async { implicit request =>
      Future(ProjectFsm(state).withSecurity(token)).flatMap {
        _ ! GetHistory(projectId)
      }.map { history =>
        if (history.isEmpty) errors.toResult(CommonErrors.NotFound("history of project", projectId), None)
        else Ok(success(Json.obj("history" -> Json.toJson(history))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get history of project $projectId"))
      }
    }
  }
}
