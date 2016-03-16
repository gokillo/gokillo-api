/*#
  * @file Apps.scala
  * @begin 28-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.auth

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import brix.crypto.Secret
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.common.{CommonErrors, DaoErrors}
import services.auth._
import services.auth.mongo._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import utils.common.typeExtensions._
import models.common.Id
import models.auth._
import models.auth.ApiConsumer._
import models.auth.Credentials._
import models.auth.TokenType.{Authorization => Auth}
import models.auth.TokenType._

@Api(
  value = "/auth/apps",
  description = "Register and manage API consumers",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Apps extends Controller with Security {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors {}

  protected val apiConsumerService: ApiConsumerDaoServiceComponent#ApiConsumerDaoService = new ApiConsumerDaoServiceComponent
    with MongoApiConsumerDaoComponent {
  }.daoService.asInstanceOf[ApiConsumerDaoServiceComponent#ApiConsumerDaoService]

  protected val accountService: AccountDaoServiceComponent#AccountDaoService = new AccountDaoServiceComponent
    with MongoAccountDaoComponent {
  }.daoService.asInstanceOf[AccountDaoServiceComponent#AccountDaoService]

  /**
    * Creates an action that authorizes the specified operation only if issued
    * by the specified app.
    *
    * @param appIdOrName  The identifier or name of the app allowed to issue `operation`.
    * @param operation    The name of the operation to authorize.
    * @param action       A function that takes a `Token` and returns an `EssentialAction`.
    * @return             An authorized action.
    */
  protected def Authorized(appIdOrName: String, operation: String)(action: Token => EssentialAction): EssentialAction = Authorized(operation) { token =>
    EssentialAction { implicit request =>
      Iteratee.flatten(
        apiConsumerService.find(
          Id(if (appIdOrName.isObjectId) "id" else "name", Some(appIdOrName)),
          Some(Json.obj("$include" -> Json.arr("ownerId")))
        ).map {
          case Some(apiConsumer) if apiConsumer.ownerId.get == token.account => action(token)(request)
          case Some(apiConsumer) => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.NotAuthorized(
                "operation", operation, "app", appIdOrName,
                s"user ${apiConsumer.ownerId.get} is not the owner of account ${token.account}"
              ),
              Some(s"request $requestUriWithMethod not authorized")
            ))
          case _ => Done[Array[Byte], Result](errors.toResult(CommonErrors.NotFound("app", appIdOrName), None))
        }
      )
    }   
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "create",
    value = "Creates a new app",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "App data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing create app request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "app",
    value = "The app data",
    required = true,
    dataType = "models.auth.api.ApiConsumer",
    paramType = "body")))
  def create = Authorized("create") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[ApiConsumer](apiConsumerFormat(Some(false))).fold(
        valid = { apiConsumer =>
          accountService.insert(Account(
            ownerId = Some(apiConsumerService.generateId),
            roles = Some(List(Role.Guest.id))
          )).flatMap { newAccount =>
            apiConsumer.id = newAccount.ownerId
            apiConsumer.accountId = newAccount.id
            apiConsumer.ownerId = Some(token.account)
            apiConsumer.apiKey = Some(Secret(AuthPlugin.ApiKeyLength).value)

            // only superusers are allowed to register native apps
            if (!token.roles.contains(Role.Superuser.id)) apiConsumer.nativeOpt = None

            apiConsumerService.insert(apiConsumer).flatMap { newApp =>
              Logger.debug(s"created app ${newApp.id.get}")
              Future.successful(Created(success).withHeaders(
                LOCATION -> s"$requestUri/${newApp.id.get}"
              ))
            }
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create app ${apiConsumer.name.get}"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create app request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "authenticate",
    value = "Authenticates an app",
    notes = "Returns the JSON Web Token to be used in any subsequent request",
    response = classOf[models.auth.api.Jwt],
    authorizations = Array())
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "App authentication failed"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 422, message = "Credentials with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing authentication request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "credentials",
    value = "The app credentials",
    required = true,
    dataType = "models.auth.api.Credentials",
    paramType = "body")))
  def authenticate = Action.async(parse.json) { implicit request =>
    request.body.validate[Credentials](credentialsFormat).fold(
      valid = { credentials =>
        val id = Id(
          if (credentials.principal.isObjectId) "id" else "name",
          Some(credentials.principal)
        )

        apiConsumerService.find(
          id, Some(Json.obj("$include" -> Json.arr("apiKey", "accountId")))
        ).flatMap {
          case Some(apiConsumer) =>
            if (credentials.secret != apiConsumer.apiKey.get) {
              Future.failed(AuthErrors.AuthenticationFailed("api key", "app", apiConsumer.id.get))
            } else {
              accountService.find(apiConsumer.accountId, Some(Json.obj("$exclude" -> Json.arr("_version")))).flatMap {
                case Some(account) => AuthPlugin.createToken(
                    Browse, apiConsumer.apiKey,
                    Some(account), apiConsumer.name,
                    false // the duration of browse tokens is not extendable
                  ).map { token =>
                    Logger.debug(s"app ${id.value.get} authenticated successfully")
                    Ok(success(Json.obj("token" -> token.asJwt)))
                  }
                case _ => Future.failed(CommonErrors.NotFound("account", apiConsumer.accountId.get))
              }
            }
          case _ => Future.failed(CommonErrors.NotFound("app", id.value.get))
        }.recover { case NonFatal(e) =>
          errors.toResult(e, Some(s"could not authenticate app ${id.value.get}"))
        }
      },
      invalid = { validationErrors =>
        val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
        Future.successful(errors.toResult(e, Some("error parsing app authentication request")))
      }
    )
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "update",
    value = "Updates an app",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id or invalid app data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update app request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "app",
    value = "The update data",
    required = true,
    dataType = "models.auth.api.ApiConsumer",
    paramType = "body")))
  def update(
    @ApiParam(
      name = "appId",
      value = "The id of the app to update",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized(appId, "update") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[ApiConsumer](apiConsumerFormat(Some(true))).fold(
        valid = { update =>
          // api key cannot be modified
          if (update.apiKey.isDefined) {
            Logger.warn(s"api key of app $appId was set for update: ignored")
            update.apiKey = None
          }

          apiConsumerService.findAndUpdate(Id(Some(appId)).asJson, update.asJson).map {
            case Some(_) =>
              Logger.debug(s"updated app $appId")
              Ok(success)
            case _ => errors.toResult(CommonErrors.NotFound("app", appId), None)
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update app $appId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update app request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "delete",
    value = "Deletes an app",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete app request")))
  def delete(
    @ApiParam(
      name = "appId",
      value = "The id of the app to delete",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized(appId, "delete") { token =>
    Action.async { implicit request =>
      apiConsumerService.findAndRemove(appId).map {
        case Some(removedApiConsumer) =>
          Logger.debug(s"deleted app ${removedApiConsumer.id.get}")
          accountService.findAndRemove(removedApiConsumer.accountId).map { _.foreach { removedAccount =>
            Logger.debug(s"deleted account ${removedAccount.id.get}")
          }}
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("app", appId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete app $appId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds an app",
    response = classOf[models.auth.api.ApiConsumer])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 500, message = "Error processing find app request")))
  def find(
    @ApiParam(
      name = "appId",
      value = "The id of the app to find",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized(appId, "find") { request =>
    Action.async { implicit request =>
      apiConsumerService.find(
        appId,
        Some(Json.obj("$exclude" -> Json.arr("_version")))
      ).map {
        case Some(apiConsumer) => Ok(success(Json.obj("apiConsumer" -> apiConsumer.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("app", appId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find app $appId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findByName",
    value = "Finds an app by name",
    response = classOf[models.auth.api.ApiConsumer])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 500, message = "Error processing find app by name request")))
  def findByName(
    @ApiParam(
      name = "name",
      value = "The name of the app to find",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(name, "findByName") { token =>
    Action.async { implicit request =>
      apiConsumerService.find(
        Json.obj("name" -> name),
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        None, 0, 1
      ).map { _.headOption match {
        case Some(apiConsumer) => Ok(success(Json.obj("apiConsumer" -> apiConsumer.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("app", name), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find app named $name"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "acquire",
    value = "Acquires an app as native",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing acquire app as native request")))
  def acquire(
    @ApiParam(
      name = "appId",
      value = "The id of the app to acquire",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized("acquire") { token =>
    Action.async(parse.json) { implicit request =>
      apiConsumerService.findAndUpdate(Id(Some(appId)).asJson, Json.obj("native" -> true)).map {
        case Some(_) =>
          Logger.debug(s"acquired app $appId as native")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("app", appId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not acquire app $appId as native"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "drop",
    value = "Drops an app as native",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing drop app as native request")))
  def drop(
    @ApiParam(
      name = "appId",
      value = "The id of the app to drop",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized("drop") { token =>
    Action.async(parse.json) { implicit request =>
      apiConsumerService.findAndUpdate(Id(Some(appId)).asJson, Json.obj("native" -> JsNull)).map {
        case Some(_) =>
          Logger.debug(s"dropped app $appId as native")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("app", appId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not drop app $appId as native"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getApiKeyN",
    value = "Gets the secret API key of a native app",
    response = classOf[String],
    authorizations = Array())
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 500, message = "Error processing get API key request")))
  def getApiKeyN(
    @ApiParam(
      name = "appIdOrName",
      value = "The id or name of the native app to get the API key for",
      required = true)
    @PathParam("appIdOrName")
    appIdOrName: String
  ) = Action.async { implicit request =>
    apiConsumerService.find(
      Id(if (appIdOrName.isObjectId) "id" else "name", Some(appIdOrName)),
      Some(Json.obj("$include" -> Json.arr("apiKey", "native")))
    ).map {
      case Some(apiConsumer) if apiConsumer.native => Ok(success(Json.obj("apiKey" -> apiConsumer.apiKey)))
      case Some(apiConsumer) => errors.toResult(
          AuthErrors.NotAuthorized("operation", "getApiKeyN", "app", appIdOrName, s"app $appIdOrName is not native"),
          Some(s"request $requestUriWithMethod not authorized")
        )
      case _ => errors.toResult(CommonErrors.NotFound("app", appIdOrName), None)
    }.recover { case NonFatal(e) =>
      errors.toResult(e, Some(s"could not get api key for native app $appIdOrName"))
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getApiKey",
    value = "Gets the secret API key of an app",
    response = classOf[String],
    authorizations = Array())
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App not found"),
    new ApiResponse(code = 500, message = "Error processing get API key request")))
  def getApiKey(
    @ApiParam(
      name = "appIdOrName",
      value = "The id or name of the app to get the API key for",
      required = true)
    @PathParam("appIdOrName")
    appIdOrName: String
  ) = Authorized(appIdOrName, "getApiKey") { token =>
    Action.async { implicit request =>
      apiConsumerService.find(
        Id(if (appIdOrName.isObjectId) "id" else "name", Some(appIdOrName)),
        Some(Json.obj("$include" -> Json.arr("apiKey")))
      ).map {
        case Some(apiConsumer) => Ok(success(Json.obj("apiKey" -> apiConsumer.apiKey)))
        case _ => errors.toResult(CommonErrors.NotFound("app", appIdOrName), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get api key for app $appIdOrName"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getAccount",
    value = "Gets the account of an API consumer",
    response = classOf[models.auth.api.Account])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid app id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "App or account not found"),
    new ApiResponse(code = 500, message = "Error processing get app account request")))
  def getAccount(
    @ApiParam(
      name = "appId",
      value = "The id of the app to get the account for",
      required = true)
    @PathParam("appId")
    appId: String
  ) = Authorized("getAccount") { token =>
    Action.async { implicit request =>
      apiConsumerService.find(
        appId,
        Some(Json.obj("$include" -> Json.arr("accountId")))
      ).flatMap {
        case Some(apiConsumer) =>
          accountService.find(apiConsumer.accountId, Some(Json.obj("$exclude" -> Json.arr("_version")))).flatMap {
            case Some(account) => Future.successful(Ok(success(Json.obj("account" -> account.asJson))))
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound("account", apiConsumer.accountId.get), None))
          }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("app", appId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get account for app $appId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the registered apps",
    response = classOf[models.auth.api.ApiConsumer],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No apps found"),
    new ApiResponse(code = 500, message = "Error processing list apps request")))
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
      val selector = if (token.roles.contains(Role.Superuser.id)) {
        Json.obj()
      } else {
        Json.obj("ownerId" -> token.account)
      }

      apiConsumerService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$asc" -> Json.arr("name"))),
        page, perPage
      ).map { apiConsumers =>
        if (apiConsumers.isEmpty) errors.toResult(CommonErrors.EmptyList("apps"), None)
        else Ok(success(Json.obj("apiConsumers" -> Json.toJson(apiConsumers))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list apps"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByOwner",
    value = "Lists the apps by owner",
    response = classOf[models.auth.api.ApiConsumer],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid owner id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No apps found"),
    new ApiResponse(code = 500, message = "Error processing list apps by owner request")))
  def listByOwner(
    @ApiParam(
      name = "ownerId",
      value = "The id of the owner to list the apps for",
      required = true)
    @PathParam("ownerId")
    ownerId: String,

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
  ) = Authorized("listByOwner") { token =>
    Action.async { implicit request =>
      apiConsumerService.find(
        Json.obj("ownerId" -> ownerId),
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$asc" -> Json.arr("name"))),
        page, perPage
      ).map { apiConsumers =>
        if (apiConsumers.isEmpty) errors.toResult(CommonErrors.EmptyList("apps", "owner", ownerId), None)
        else Ok(success(Json.obj("apiConsumers" -> Json.toJson(apiConsumers))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list apps by owner"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByName",
    value = "Lists the apps by name",
    response = classOf[models.auth.api.ApiConsumer],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No apps found"),
    new ApiResponse(code = 500, message = "Error processing list apps by name request")))
  def listByName(
    @ApiParam(
      name = "name",
      value = "The full or partial name of the apps to list",
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
  ) = Authorized("listByName") { token =>
    Action.async { implicit request =>
      val selector = if (token.roles.contains(Role.Superuser.id)) {
        Json.obj("$like" -> Json.obj("name" -> name))
      } else {
        Json.obj("$like" -> Json.obj("name" -> name), "ownerId" -> token.account)
      }

      apiConsumerService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$asc" -> Json.arr("name"))),
        page, perPage
      ).map { apiConsumers =>
        if (apiConsumers.isEmpty) errors.toResult(CommonErrors.EmptyList("apps"), None)
        else Ok(success(Json.obj("apiConsumers" -> Json.toJson(apiConsumers))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list apps by name"))
      }
    }
  }
}
