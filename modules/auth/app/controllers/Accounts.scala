/*#
  * @file Accounts.scala
  * @begin 21-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.auth

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.{QueryParam, PathParam}
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.common.{CommonErrors, DaoErrors}
import services.auth._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.auth.{Account, Role, Token, User}
import models.auth.TokenType._

@Api(value = "/auth/users")
trait Accounts extends Controller with Security {

  protected val errors: CommonErrors with DaoErrors with AuthErrors
  protected val userService: UserDaoServiceComponent#UserDaoService
  protected val accountService: AccountDaoServiceComponent#AccountDaoService
  protected def Authorized(userId: String, operation: String)(action: Token => EssentialAction): EssentialAction

  @ApiOperation(
    httpMethod = "POST",
    nickname = "createAccount",
    value = "Creates a new account",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or account data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 409, message = "Account name already used"),
    new ApiResponse(code = 422, message = "Account data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing create account request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "account",
    value = "The account data",
    required = true,
    dataType = "models.auth.api.Account",
    paramType = "body")))
  def createAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to create the account for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "createAccount") { token =>
    Action.async(parse.json) { implicit request =>
      Future.successful(errors.toResult(
        CommonErrors.NotImplemented("action", requestUriWithMethod), None
      ))
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "activateAccount",
    value = "Activates an account",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing activate account request")))
  def activateAccount = Authorized("activateAccount", Activation) { token =>
    Action.async { implicit request =>
      val userId = token.subject
      val accountId = token.account

      userService.activateAccount(userId, accountId).map {
        case Some(account) =>
          Logger.debug(s"account $accountId activated for user $userId")
          Ok(success)
        case _ =>
          /* currently only one account - the default - is supported */
          errors.toResult(
            CommonErrors.ElementNotFound("account", accountId, "user", userId), None
          )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not activate account $accountId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "assignRolesToAccount",
    value = "Assigns one or more roles to an account",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid role list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Role list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing assign roles to account request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "roles",
    value = "The roles to assign to the account; valid values are superuser, auditor, editor, and member",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def assignRolesToAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user that owns the account to assign the roles to",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("assignRolesToAccount") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          userService.findAccount(userId, index).flatMap {
            case Some(account) =>
              accountService.addRoles(account.id.get, roles.map(Role(_))).map { _ =>
                Ok(success)
              }
            case _ =>
              Future.successful(errors.toResult(
                CommonErrors.ElementNotFound("account", index.toString, "user", userId), None
              ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"""could not assign role(s) ${roles.mkString(", ")} to user $userId"""))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing assign roles to account request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "assignRolesToAccountByName",
    value = "Assigns one or more roles to an account by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid role list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Role list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing assign roles to account by name request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "roles",
    value = "The roles to assign to the account; valid values are superuser, auditor, editor, and member",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def assignRolesToAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user that owns the account to assign the roles to",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized("assignRolesToAccountByName") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          userService.findAccountByName(userId, name).flatMap {
            case Some(account) =>
              accountService.addRoles(account.id.get, roles.map(Role(_))).map { _ =>
                Ok(success)
              }
            case _ =>
              Future.successful(errors.toResult(
                CommonErrors.ElementNotFound("account", name, "user", userId), None
              ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"""could not assign role(s) ${roles.mkString(", ")} to user $userId"""))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing assign roles to account by name request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unassignRolesFromAccount",
    value = "Unassigns one ore more roles from an account",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid role list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Role list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing unassign roles from account request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "roles",
    value = "The roles to unassign from the account; valid values are superuser, auditor, editor, and member",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def unassignRolesFromAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user that owns the account to unassign the roles from",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("unassignRolesFromAccount") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          userService.findAccount(userId, index).flatMap {
            case Some(account) =>
              accountService.removeRoles(account.id.get, roles.map(Role(_))).map { _ =>
                Ok(success)
              }
            case _ => Future.successful(errors.toResult(
              CommonErrors.ElementNotFound("account", index.toString, "user", userId), None
            ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"""could not unassign role(s) ${roles.mkString(", ")} to user $userId"""))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing unassign roles from account request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unassignRolesFromAccountByName",
    value = "Unassigns one ore more roles from an account by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid role list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Role list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing unassign roles from account by name request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "roles",
    value = "The roles to unassign from the account; valid values are superuser, auditor, editor, and member",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def unassignRolesFromAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user that owns the account to unassign the roles from",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized("unassignRolesFromAccountByName") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          userService.findAccountByName(userId, name).flatMap {
            case Some(account) =>
              accountService.removeRoles(account.id.get, roles.map(Role(_))).map { _ =>
                Ok(success)
              }
            case _ =>
              Future.successful(errors.toResult(
                CommonErrors.ElementNotFound("account", name, "user", userId), None
              ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"""could not unassign role(s) ${roles.mkString(", ")} to user $userId"""))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing unassign roles from account by name request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setDefaultAccount",
    value = "Sets an account as the default",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing set default account request")))
  def setDefaultAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to set the default account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "setDefaultAccount") { token =>
    Action.async { implicit request =>
      userService.setDefaultAccount(userId, index).map {
        case Some(_) =>
          Logger.debug(s"set account $index as the default account of user $userId")
          Ok(success)
        case _ =>
          errors.toResult(
            CommonErrors.ElementNotFound("account", index.toString, "user", userId), None
          )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set account $index as the default account of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setDefaultAccountByName",
    value = "Sets an account as the default by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing set default account by name request")))
  def setDefaultAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to set the default account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "setDefaultAccountByName") { token =>
    Action.async { implicit request =>
      userService.setDefaultAccountByName(userId, name).map {
        case Some(_) =>
          Logger.debug(s"set account named $name as the default account of user $userId")
          Ok(success)
        case _ =>
          errors.toResult(
            CommonErrors.ElementNotFound("account", name, "user", userId), None
          )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set account named $name as the default account of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "shareAccount",
    value = "Shares an account with other users",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 202, message = "Could not share the account with one or more users"),
    new ApiResponse(code = 400, message = "Invalid user id or invalid grantee list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Grantee list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing share account request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "userIds",
    value = "The ids of the users to share the account with",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def shareAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to share the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "shareAccount") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          Future.successful(errors.toResult(
            CommonErrors.NotImplemented("action", requestUriWithMethod), None
          ))
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing share account request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "shareAccountByName",
    value = "Shares an account with other users by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 202, message = "Could not share the account with one or more users"),
    new ApiResponse(code = 400, message = "Invalid user id or invalid grantee list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Grantee list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing share account by name request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "userIds",
    value = "The ids of the users to share the account with",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def shareAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to share the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "shareAccountByName") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          Future.successful(errors.toResult(
            CommonErrors.NotImplemented("action", requestUriWithMethod), None
          ))
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing share account by name request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unshareAccount",
    value = "Unshares an account from a grantee",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 202, message = "Could not unshare the account from one or more grantees"),
    new ApiResponse(code = 400, message = "Invalid user id or invalid grantee list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Grantee list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing unshare account request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "userIds",
    value = "The ids of the users to unshare the account from",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def unshareAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to unshare the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "unshareAccount") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          Future.successful(errors.toResult(
            CommonErrors.NotImplemented("action", requestUriWithMethod), None
          ))
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing unshare account request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unshareAccountByName",
    value = "Unshares an account from a grantee by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 202, message = "Could not unshare the account from one or more grantees"),
    new ApiResponse(code = 400, message = "Invalid user id or invalid grantee list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Grantee list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing unshare account by name request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "userIds",
    value = "The ids of the users to unshare the account from",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def unshareAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to unshare the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "unshareAccountByName") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { roles =>
          Future.successful(errors.toResult(
            CommonErrors.NotImplemented("action", requestUriWithMethod), None
          ))
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing unshare account by name request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteAccount",
    value = "Deletes an account",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete account request")))
  def deleteAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "deleteAccount") { token =>
    Action.async { implicit request =>
      Future.successful(errors.toResult(
        CommonErrors.NotImplemented("action", requestUriWithMethod), None
      ))
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteAccountByName",
    value = "Deletes an account by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete account by name request")))
  def deleteAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "deleteAccountByName") { token =>
    Action.async { implicit request =>
      Future.successful(errors.toResult(
        CommonErrors.NotImplemented("action", requestUriWithMethod), None
      ))
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findAccount",
    value = "Finds an account",
    response = classOf[models.auth.api.Account])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 500, message = "Error processing find account request")))
  def findAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the account",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized("findAccount") { token =>
    Action.async { implicit request =>
      userService.findAccount(userId, index).map {
        case Some(account) => Ok(success(Json.obj("account" -> account.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("account", index.toString, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find account $index of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findAccountByName",
    value = "Finds an account by name",
    response = classOf[models.auth.api.Account])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 500, message = "Error processing find account by name request")))
  def findAccountByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the account for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The account name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized("findAccountByName") { token =>
    Action.async { implicit request =>
      userService.findAccountByName(userId, name).map {
        case Some(account) => Ok(success(Json.obj("account" -> account.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("account", name, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find account named $name of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findDefaultAccount",
    value = "Finds the default account",
    response = classOf[models.auth.api.Account])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find default account request")))
  def findDefaultAccount(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the account for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("findDefaultAccount") { token =>
    Action.async { implicit request =>
      userService.findDefaultAccount(userId).map {
        case Some(account) => Ok(success(Json.obj("account" -> account.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("account", "default", "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find default account of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listAccounts",
    value = "Lists the accounts of a user",
    response = classOf[models.auth.api.Account],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "User does not have required privileges"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing list accounts request")))
  def listAccounts(
    @ApiParam(
      name = "userId",
      value = "The id of the user to list the accounts for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("listAccounts") { token =>
    Action.async { implicit request =>
      userService.findAccounts(userId).map { accounts =>
        if (accounts.isEmpty) errors.toResult(CommonErrors.EmptyList("accounts", "user", userId), None)
        else Ok(success(Json.obj("accounts" -> Json.toJson(accounts))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list accounts of user $userId"))
      }
    }
  }
}
