/*#
  * @file Users.scala
  * @begin 6-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.auth

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.collection.immutable.{List => ImmutableList}
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.iteratee.Done
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.common.FsController
import services.common._
import services.auth._
import services.auth.mongo._
import services.auth.users.UserFsm
import services.auth.users.UserFsm._
import utils.common.typeExtensions._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import utils.auth.EmailHelper
import models.common.{Comment, Id}
import models.common.Comment._
import models.auth._
import models.auth.Credentials._
import models.auth.User._
import models.auth.Password._
import models.auth.PasswordChange._
import models.auth.TokenType.{Authorization => Auth}
import models.auth.TokenType._
import models.auth.Role._

@Api(
  value = "/auth/users",
  description = "Register users and manage their accounts",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Users extends Controller
  with Security
  with FsController
  with Addresses
  with Accounts {

  protected val errors = new CommonErrors with DaoErrors with FsmErrors with AuthErrors {}

  protected implicit val fsService: FsServiceComponent#FsService = new DefaultFsServiceComponent
    with MongoUserFsComponent {
  }.fsService

  protected val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
    with MongoUserDaoComponent {
  }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

  protected val accountService: AccountDaoServiceComponent#AccountDaoService = new AccountDaoServiceComponent
    with MongoAccountDaoComponent {
  }.daoService.asInstanceOf[AccountDaoServiceComponent#AccountDaoService]

  /**
    * Creates an action that authorizes the specified operation only if issued
    * by the specified user.
    *
    * @param userId     The identifier of the user allowed to issue `operation`.
    * @param operation  The name of the operation to authorize.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  protected def Authorized(userId: String, operation: String)(action: Token => EssentialAction): EssentialAction = Authorized(operation) { token =>
    EssentialAction { implicit request =>
      if (token.accountOwner == userId || token.roles.contains(Superuser.id) || token.roles.contains(Auditor.id))
        action(token)(request)
      else Done(errors.toResult(
        AuthErrors.NotAuthorized("operation", operation, "user", token.username, s"user $userId is not the owner of account ${token.account}"),
        Some(s"request $requestUriWithMethod not authorized")
      ))
    }   
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "create",
    value = "Creates a new user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user data"),
    new ApiResponse(code = 409, message = "Username or email address already taken"),
    new ApiResponse(code = 422, message = "User data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing create user request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "user",
    value = "The user data",
    required = true,
    dataType = "models.auth.api.User",
    paramType = "body")))
  def create = Authorized("create", Browse) { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[User](userFormat(Some(false))).fold(
        valid = { user =>
          user.password = user.password.map(password => password.hash)
          user.publicOpt = Some(true)
          user.metaAccounts = Some(ImmutableList(MetaAccount(
            Some(accountService.generateId),
            user.username,
            None, Some(true)
          )))
          (New ! Save(user)).flatMap { r =>
            // r._1: one of the UserFsm values
            // r._2: new instance of the User class
            Logger.debug(s"created user ${r._2.id.get}")
            accountService.insert(Account(
              id = r._2.metaAccounts.get(0).id,
              ownerId = r._2.id,
              roles = Some(ImmutableList(Role.Member.id))
            ))
          }.flatMap { newAccount => AuthPlugin.createToken(
            Activation, Some(token.apiKey),
            Some(newAccount), user.username
          ).map { token =>
            Logger.debug(s"created account ${newAccount.id.get} for user ${newAccount.ownerId.get}")
            EmailHelper.sendEmailVerificationEmail(user, token.asJwt)
            Created(success).withHeaders(
              LOCATION -> s"$requestUri/${newAccount.ownerId.get}"
            )
          }}.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create user ${user.username.get}"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create user request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "authenticate",
    value = "Authenticates a user",
    notes = "Returns the JSON Web Token to be used in any subsequent request",
    response = classOf[models.auth.api.Jwt])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "User authentication failed"),
    new ApiResponse(code = 404, message = "User or account not found"),
    new ApiResponse(code = 422, message = "Credentials with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing authentication request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "credentials",
    value = "The user credentials",
    required = true,
    dataType = "models.auth.api.Credentials",
    paramType = "body")))
  def authenticate = Authorized("authenticate", Browse) { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Credentials](credentialsFormat).fold(
        valid = { credentials =>
          val id = Id(
            if (credentials.principal.isEmailAddress) "email" else "username",
            Some(credentials.principal)
          )

          userService.find(
            id, Some(Json.obj("$include" -> Json.arr("username", "email", "password", "firstName", "lastName", "metaAccounts")))
          ).flatMap {
            case Some(user) => user.password.map { userPassword =>
              val password = Password(credentials.secret, userPassword.salt)
              if (password.hash != userPassword) {
                Future.failed(AuthErrors.AuthenticationFailed("password", "user", user.id.get))
              } else {
                user.metaAccounts.map(_.filter(_.default)(0)).map { defaultMetaAccount =>
                  val tokenType = if (defaultMetaAccount.active) Auth else Activation
                  accountService.find(defaultMetaAccount.id, Some(Json.obj("$exclude" -> Json.arr("_version")))).flatMap {
                    case Some(account) => AuthPlugin.createToken(
                        tokenType, Some(token.apiKey),
                        Some(account), user.username
                      ).map { token => tokenType match {
                        case Auth =>
                          Logger.debug(s"user ${id.value.get} authenticated successfully")
                          Ok(success(Json.obj("token" -> token.asJwt)))
                        case _ =>
                          EmailHelper.sendEmailVerificationEmail(user, token.asJwt)
                          throw AuthErrors.RegistrationNotComplete(
                            "user", id.value.get, s"account ${defaultMetaAccount.id.get} has not been activated"
                          )
                        }}
                    case _ => Future.failed(AuthErrors.AccountMismatch(defaultMetaAccount.id.get))
                  }
                } getOrElse Future.failed(CommonErrors.NotFound("account", "default"))
              }} getOrElse Future.failed(AuthErrors.RegistrationNotComplete("user", user.id.get, "password not set"))
            case _ => Future.failed(CommonErrors.NotFound("user", id.value.get))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not authenticate user ${id.value.get}"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing user authentication request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deny",
    value = "Makes user credentials expire so that any further access is denied",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing deny request")))
  def deny = Authorized("deny") { token =>
    Action.async { implicit request =>
      AuthPlugin.discardToken(token.id).map { _ =>
        Logger.debug(s"token ${token.id} discarded: any further access to account ${token.account} is denied")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not make user credentials expire"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "decodeToken",
    value = "Gets the plain JSON representation of the current JSON Web Token",
    response = classOf[models.auth.api.Token])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired")))
  def decodeToken = Authorized("decodeToken", `*`: _*) { token =>
    Action.async { implicit request =>
      Future.successful(Ok(success(Json.obj("token" -> token.asJson))))
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "changePassword",
    value = "Changes the password of a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or current password does not match"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 409, message = "New password same as current password"),
    new ApiResponse(code = 422, message = "Password data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing change password request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "passwordChange",
    value = "The current password and the new password",
    required = true,
    dataType = "models.auth.api.PasswordChange",
    paramType = "body")))
  def changePassword(
    @ApiParam(
      name = "userId",
      value = "The id of the user to change the password for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "changePassword") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[PasswordChange].fold(
        valid = { passwordChange =>
          userService.find(
            userId,
            Some(Json.obj("$include" -> Json.arr("email", "password", "firstName", "lastName")))
          ).flatMap {
            case Some(user) => {
              val password = Password(passwordChange.currentPassword.value, user.password.get.salt)
              if (password.hash != user.password.get) {
                Future.failed(AuthErrors.PasswordChangeFailed("user", userId, "current password does not match"))
              } else if (passwordChange.currentPassword.value == passwordChange.newPassword.value) {
                Future.failed(AuthErrors.PasswordChangeFailed("user", userId, "new password same as current password"))
              } else {
                userService.findAndUpdate(
                  Json.obj("id" -> userId, "password" -> Json.toJson(user.password)),
                  Json.obj("password" -> Json.toJson(passwordChange.newPassword.hash))
                ).map {
                  case Some(_) =>
                    EmailHelper.sendPasswordChangeNotificationEmail(user)
                    Logger.debug(s"changed password of user $userId")
                    Ok(success)
                  case _ => throw DaoErrors.StaleObject(userId, userService.collectionName)
                }
              }
            }
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", userId), None))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not change password"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing change password request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "triggerPasswordReset",
    value = "Triggers the password reset for a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid username or invalid email address"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing trigger password reset request")))
  def triggerPasswordReset(
    @ApiParam(
      name = "usernameOrEmail",
      value = "The username or email address of the user to trigger the password reset for",
      required = true)
    @PathParam("usernameOrEmail")
    usernameOrEmail: String
  ) = Authorized("triggerPasswordReset", Browse) { token =>
    Action.async { implicit request =>
      val id = Id(
        if (usernameOrEmail.isEmailAddress) "email" else "username",
        Some(usernameOrEmail)
      )

      userService.find(
        id, Some(Json.obj("$include" -> Json.arr("username", "email", "firstName", "lastName", "metaAccounts")))
      ).flatMap {
        case Some(user) =>
          val defaultMetaAccount = user.metaAccounts.get.filter(_.default)(0)
          accountService.find(defaultMetaAccount.id).flatMap {
            case Some(account) => AuthPlugin.createToken(
                Reset, Some(token.apiKey),
                Some(account), user.username
              ).map { case token =>
                EmailHelper.sendPasswordResetEmail(user, token.asJwt)
                Ok(success)
              }
            case _ => Future.failed(AuthErrors.AccountMismatch(defaultMetaAccount.id.get))
          }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", id.value.get), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not trigger password reset"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "resetPassword",
    value = "Resets the password of a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 409, message = "New password same as current password"),
    new ApiResponse(code = 422, message = "Password data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing reset password request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "password",
    value = "The new password",
    required = true,
    dataType = "models.auth.api.Password",
    paramType = "body")))
  def resetPassword = Authorized("resetPassword", Reset) { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Password].fold(
        valid = { newPassword =>
          userService.find(
            token.subject,
            Some(Json.obj("$include" -> Json.arr("password")))
          ).flatMap {
            case Some(user) =>
              userService.findAndUpdate(
                Json.obj("id" -> user.id.get, "password" -> Json.toJson(user.password)),
                Json.obj("password" -> Json.toJson(Password(newPassword.value, newPassword.salt).hash))
              ).map {
                case Some(_) =>
                  Logger.debug(s"reset password of user ${user.id.get}")
                  Ok(success)
                case _ => throw DaoErrors.StaleObject(user.id.get, userService.collectionName)
              }
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", token.subject), None))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not reset password"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing reset password request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "update",
    value = "Updates a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 409, message = "Username or email address already taken"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing update user request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "user",
    value = "The update data",
    required = true,
    dataType = "models.auth.api.User",
    paramType = "body")))
  def update(
    @ApiParam(
      name = "userId",
      value = "The id of the user to update",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "update") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[User](userFormat(Some(true))).fold(
        valid = { update => update.username match {
          case Some(_) => Future.successful(errors.toResult(CommonErrors.NotAllowed("update", "username"), None))
          case _ =>
            // prevent password from being updated here; use specific api instead
            if (update.password.isDefined) {
              Logger.warn(s"password of user $userId was set for update: ignored")
              update.password = None
            }

            // prevent meta-accounts from being updated here; use specific api instead
            if (update.metaAccounts.isDefined) {
              Logger.warn(s"one or more accounts of user $userId were set for update: ignored")
              update.metaAccounts = None
            }

            userService.findAndUpdate(Id(Some(userId)).asJson, update.asJson).map {
              case Some(oldUser) =>
                update.email.foreach { email => if (email != oldUser.email.get) {
                  val defaultMetaAccount = oldUser.metaAccounts.get.filter(_.default)(0)
                  accountService.find(defaultMetaAccount.id).map { _.foreach { account =>
                    AuthPlugin.createToken(
                      Reset, Some(token.apiKey),
                      Some(account), oldUser.username
                    ).map { token =>
                      EmailHelper.sendEmailVerificationEmail(oldUser, token.asJwt)
                    }
                  }}
                }}
                Logger.debug(s"updated user $userId")
                Ok(success)
              case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
            }.recover { case NonFatal(e) =>
              errors.toResult(e, Some(s"could not update user $userId"))
            }
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update user request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "delete",
    value = "Deletes a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing delete user request")))
  def delete(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "delete") { token =>
    Action.async { implicit request =>
      userService.findAndRemove(userId).map {
        case Some(removedUser) =>
          Logger.debug(s"deleted user ${removedUser.id.get}")
          removedUser.metaAccounts.foreach { _.foreach { metaAccount =>
            accountService.findAndRemove(metaAccount.id).map { _.foreach { removedAccount =>
              Logger.debug(s"deleted account ${removedAccount.id.get}")
            }}
          }}
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds a user",
    response = classOf[models.auth.api.User])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find user request")))
  def find(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("find", Auth, Browse) { token =>
    Action.async { implicit request =>
      var selector = Json.obj("id" -> userId)
      var exclude = Json.arr("password", "public", "addresses", "metaAccounts", "_version")

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("public" -> true)
        exclude = exclude :+ JsString("email")
      }

      userService.find(
        selector, Some(Json.obj("$exclude" -> exclude)), None, 0, 1
      ).map { _.headOption match {
        case Some(user) => Ok(success(Json.obj("user" -> user.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findByEmail",
    value = "Finds a user by email",
    response = classOf[models.auth.api.User])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find user by email request")))
  def findByEmail(
    @ApiParam(
      name = "email",
      value = "The email address of the user to find",
      required = true)
    @PathParam("email")
    email: String
  ) = Authorized("findByEmail", Auth, Browse) { token =>
    Action.async { implicit request =>
      var selector = Json.obj("email" -> email)
      var exclude = Json.arr("password", "public", "addresses", "metaAccounts", "_version")

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("public" -> true)
        exclude = exclude :+ JsString("email")
      }

      userService.find(
        selector, Some(Json.obj("$exclude" -> exclude)), None, 0, 1
      ).map { _.headOption match {
        case Some(user) => Ok(success(Json.obj("user" -> user.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("user", email), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find user $email"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findByUsername",
    value = "Finds a user by username",
    response = classOf[models.auth.api.User])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find user by username request")))
  def findByUsername(
    @ApiParam(
      name = "username",
      value = "The username of the user to find",
      required = true)
    @PathParam("username")
    username: String
  ) = Authorized("findByUsername", Auth, Browse) { token =>
    Action.async { implicit request =>
      var selector = Json.obj("username" -> username)
      var exclude = Json.arr("password", "public", "addresses", "metaAccounts", "_version")

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("public" -> true)
        exclude = exclude :+ JsString("email")
      }

      userService.find(
        selector, Some(Json.obj("$exclude" -> exclude)), None, 0, 1
      ).map { _.headOption match {
        case Some(user) => Ok(success(Json.obj("user" -> user.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("user", username), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find user $username"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findByAccountId",
    value = "Finds a user by account id",
    response = classOf[models.auth.api.User])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find user by account id request")))
  def findByAccountId(
    @ApiParam(
      name = "accountId",
      value = "The account id of the user to find",
      required = true)
    @PathParam("accountId")
    accountId: String
  ) = Authorized("findByAccountId", Auth, Browse) { token =>
    Action.async { implicit request =>
      userService.findByAccountId(
        accountId: Id,
        !token.roles.contains(Role.Superuser.id)
      ).map { _.headOption match {
        case Some(user) => Ok(success(Json.obj("user" -> user.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("user with account", accountId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find user with account $accountId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the registered users",
    response = classOf[models.auth.api.User],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No users found"),
    new ApiResponse(code = 500, message = "Error processing list users request")))
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
  ) = Authorized("list", Auth, Browse) { token =>
    Action.async { implicit request =>
      var selector = Json.obj()
      var exclude = Json.arr("password", "public", "addresses", "metaAccounts", "_version")

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = Json.obj("public" -> true)
        exclude = exclude :+ JsString("email")
      }

      userService.find(
        selector, Some(Json.obj("$exclude" -> exclude)),
        Some(Json.obj("$asc" -> Json.arr("username"))),
        page, perPage
      ).map { users =>
        if (users.isEmpty) errors.toResult(CommonErrors.EmptyList("users"), None)
        else Ok(success(Json.obj("users" -> Json.toJson(users))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list users"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByState",
    value = "Lists all the users in a given state",
    response = classOf[models.auth.api.User],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid state"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No users found"),
    new ApiResponse(code = 500, message = "Error processing list users by state request")))
  def listByState(
    @ApiParam(
      name = "state",
      value = "The state of the users to list",
      allowableValues = "registered,awaitingVerification,verification,approved",
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
  ) = Authorized("listByState") { token =>
    Action.async { implicit request =>
      Future(UserFsm(state).withSecurity(token)).flatMap {
        _ ! List(None, None, None, page, perPage)
      }.map { users =>
        if (users.isEmpty) errors.toResult(CommonErrors.EmptyList("users"), None)
        else Ok(success(Json.obj("users" -> Json.toJson(users))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list users by state"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByUsername",
    value = "Lists all the users by username",
    response = classOf[models.auth.api.User],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No users found"),
    new ApiResponse(code = 500, message = "Error processing list users by username request")))
  def listByUsername(
    @ApiParam(
      name = "username",
      value = "The full or partial username of the users to list",
      required = true)
    @PathParam("username")
    username: String,

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
  ) = Authorized("listByUsername") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("$like" -> Json.obj("username" -> username))
      var exclude = Json.arr("password", "public", "addresses", "metaAccounts", "_version")

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("public" -> true)
        exclude = exclude :+ JsString("email")
      }

      userService.find(
        selector, Some(Json.obj("$exclude" -> exclude)),
        Some(Json.obj("$asc" -> Json.arr("username"))),
        page, perPage
      ).map { users =>
        if (users.isEmpty) errors.toResult(CommonErrors.EmptyList("users"), None)
        else Ok(success(Json.obj("users" -> Json.toJson(users))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list users by username"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "saveAvatar",
    value = "Saves the avatar of a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing save avatar request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "avatar",
    value = "The user avatar",
    required = true,
    dataType = "file",
    paramType = "body")))
  def saveAvatar(
    @ApiParam(
      name = "userId",
      value = "The id of the user to save the avatar for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "saveAvatar") { token =>
    Action.async(fsBodyParser) { implicit request =>
      userService.find(
        userId, Some(Json.obj("$include" -> Json.arr("id")))
      ).flatMap {
        case Some(user) =>
          val result = for {
            file <- request.body.files.head.ref
            update <- {
              fsService.update(
                file.id,
                Json.obj("metadata" -> Json.obj("userId" -> userId, "category" -> "avatar"))
              ).map { _ =>
                fsService.remove(Json.obj(
                  "$lt" -> Json.obj("uploadDate" -> file.uploadDateMillis),
                  "metadata.userId" -> userId, "metadata.category" -> "avatar"
                ))
              }
            }
          } yield update

          result.map { _ =>
            Logger.debug(s"saved avatar for user $userId")
            Created(success).withHeaders(
              LOCATION -> requestUri
            )
          }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", userId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not save avatar for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteAvatar",
    value = "Deletes the avatar of a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing delete avatar request")))
  def deleteAvatar(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the avatar for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "deleteAvatar") { token =>
    Action.async { implicit request =>
      userService.find(
        userId, Some(Json.obj("$include" -> Json.arr("id")))
      ).flatMap {
        case Some(user) =>
          fsService.remove(
            Json.obj("metadata.userId" -> userId, "metadata.category" -> "avatar")
          ).map {
            case n if n > 0 =>
              Logger.debug(s"deleted avatar for user $userId")
              Ok(success)
            case _ => errors.toResult(CommonErrors.NotFound("avatar of user", userId), None)
          }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", userId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete avatar for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getAvatar",
    value = "Gets the avatar of a user",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 404, message = "User or avatar not found"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 500, message = "Error processing get avatar request")))
  def getAvatar(
    @ApiParam(
      name = "userId",
      value = "The id of the user to get the avatar for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("getAvatar", Auth, Browse) { token =>
    Action.async { implicit request =>
      var selector = Json.obj("id" -> userId)

      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("public" -> true)
      }

      userService.find(
        selector,
        Some(Json.obj("$include" -> Json.arr("id"))),
        None, 0, 1
      ).flatMap { _.headOption match {
        case Some(user) =>
          fsService.find(
            Json.obj("metadata.userId" -> userId, "metadata.category" -> "avatar")
          ).flatMap { _.headOption match {
            case Some(avatar) => serveFile(avatar)
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound("avatar of user", userId), None))
          }}
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("user", userId), None))
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get avatar for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "saveProof",
    value = "Saves a proof associated with a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid proof kind"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing save proof request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "proof",
    value = "The proof to save",
    required = true,
    dataType = "file",
    paramType = "body")))
  def saveProof(
    @ApiParam(
      name = "userId",
      value = "The id of the user to save the proof for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "proofOf",
      value = "The kind of proof",
      allowableValues = "address,identity,incorporation",
      required = true)
    @PathParam("proofOf")
    proofOf: String,

    @ApiParam(
      name = "page",
      value = "The page to save (0-based), or less than 0 to append",
      required = true)
    @PathParam("page")
    page: Int
  ) = Authorized("saveProof") { token =>
    Action.async(fsBodyParser) { implicit request =>
      val result = for {
        proof <- request.body.files.head.ref
        update <- {
          UserFsm(userId).withSecurity(token) ! AddProof(userId, proof, ProofOf(proofOf), page)
        }
      } yield update

      result.map { _ =>
        Logger.debug(s"saved proof of $proofOf for user $userId")
        Created(success).withHeaders(
          LOCATION -> requestUri
        )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not save proof of $proofOf for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setProofOf",
    value = "Sets the kind of a proof associated with a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid proof kind"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing set proof kind request")))
  def setProofOf(
    @ApiParam(
      name = "userId",
      value = "The id of the user to set the proof kind for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "proofId",
      value = "The id of the proof to set the kind for",
      required = true)
    @PathParam("proofId")
    proofId: String,

    @ApiParam(
      name = "proofOf",
      value = "The kind of proof",
      allowableValues = "address,identity,incorporation",
      required = true)
    @PathParam("proofOf")
    proofOf: String
  ) = Authorized("setProofOf") { token =>
    Action.async { implicit request =>
      Future(UserFsm(userId).withSecurity(token)).flatMap {
        _ ! SetProofOf(userId, proofId, ProofOf(proofOf))
      }.map { _ =>
        Logger.debug(s"set kind of proof $proofId to $proofOf for user $userId")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set kind of proof $proofId to $proofOf for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteProof",
    value = "Deletes a proof associated with a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid proof kind"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or proof not found"),
    new ApiResponse(code = 500, message = "Error processing delete proof request")))
  def deleteProof(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the proof for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "proofOf",
      value = "The kind of proof",
      allowableValues = "address,identity,incorporation",
      required = true)
    @PathParam("proofOf")
    proofOf: String
  ) = Authorized("deleteProof") { token =>
    Action.async { implicit request =>
      Future(UserFsm(userId).withSecurity(token)).flatMap {
        _ ! DeleteProof(userId, ProofOf(proofOf))
      }.map { _ =>
        Logger.debug(s"deleted proof of $proofOf for user $userId")
        Ok(success)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete proof of $proofOf for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getProof",
    value = "Gets a given proof associated with a user",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid proof kind"),
    new ApiResponse(code = 404, message = "User or proof not found"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 500, message = "Error processing get proof request")))
  def getProof(
    @ApiParam(
      name = "userId",
      value = "The id of the user to get the proof for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "proofOf",
      value = "The kind of proof",
      allowableValues = "address,identity,incorporation",
      required = true)
    @PathParam("proofOf")
    proofOf: String,

    @ApiParam(
      name = "page",
      value = "The page to get (0-based)",
      required = true)
    @PathParam("page")
    page: Int
  ) = Authorized("getProof") { token =>
    Action.async { implicit request =>
      Future(UserFsm(userId).withSecurity(token)).flatMap {
        _ ! FindProof(userId, ProofOf(proofOf), page)
      }.flatMap {
        case Some(proof) => serveFile(proof)
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound(s"proof of $proofOf for user", userId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get proof of $proofOf for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listProofs",
    value = "Lists the proofs associated with a user",
    response = classOf[models.common.api.MetaFile],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or proofs not found"),
    new ApiResponse(code = 500, message = "Error processing list proofs request")))
  def listProofs(
    @ApiParam(
      name = "userId",
      value = "The id of the user to list the proofs for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("listProofs") { token =>
    Action.async { implicit request =>
      Future(UserFsm(userId).withSecurity(token)).flatMap {
        _ ! new ListProofs(userId)
      }.map { proofs =>
        if (proofs.isEmpty) errors.toResult(CommonErrors.EmptyList("proofs", "user", userId), None)
        else Ok(success(Json.obj("proofs" -> Json.toJson(proofs)))) 
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list proofs for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "requestVerification",
    value = "Requests verification for a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing verification request")))
  def requestVerification(
    @ApiParam(
      name = "userId",
      value = "The id of the user to request verification for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("requestVerification") { token =>
    Action.async { implicit request =>
      val currentState = UserFsm(userId)
      (currentState.withSecurity(token) ! RequestVerification(userId)).map { newState =>
        Logger.debug(s"state of user $userId changed from $currentState to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not request verification for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "acquireForVerification",
    value = "Acquires a user for verification",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing acquire for verification request")))
  def acquireForVerification(
    @ApiParam(
      name = "userId",
      value = "The id of the user to acquire for verification",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("acquireForVerification") { token =>
    Action.async { implicit request =>
      val currentState = UserFsm(userId)
      (currentState.withSecurity(token) ! AcquireForVerification(userId)).map { newState =>
        Logger.debug(s"state of user $userId changed from $currentState to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not acquire user $userId for verification"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "approveVerificationRequest",
    value = "Approves a verification request",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing approve verification request")))
  def approveVerificationRequest(
    @ApiParam(
      name = "userId",
      value = "The id of the user to approve verification request for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("approveVerificationRequest") { token =>
    Action.async { implicit request =>
      val currentState = UserFsm(userId)
      (currentState.withSecurity(token) ! ApproveVerificationRequest(userId)).map { newState =>
        Logger.debug(s"state of user $userId changed from $currentState to $newState")
        Ok(success(Json.obj("state" -> newState)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not approve verification request for user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "refuseVerificationRequest",
    value = "Refuses a verification request",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing refuse verification request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "comment",
    value = "The reason the verification request was refused",
    required = true,
    dataType = "models.common.api.Comment",
    paramType = "body")))
  def refuseVerificationRequest(
    @ApiParam(
      name = "userId",
      value = "The id of the user to refuse the verification request for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("refuseVerificationRequest") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Comment].fold(
        valid = { comment =>
          val currentState = UserFsm(userId)
          (currentState ! RefuseVerificationRequest(userId, comment.text)).map { newState =>
            Logger.debug(s"state of user $userId changed from $currentState to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not refuse verification request for user $userId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing refuse verification request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "revokeApproval",
    value = "Revokes approval for a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing revoke approval request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "comment",
    value = "The reason approval was revoked",
    required = true,
    dataType = "models.common.api.Comment",
    paramType = "body")))
  def revokeApproval(
    @ApiParam(
      name = "userId",
      value = "The id of the user to revoke approval for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("revokeApproval") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Comment].fold(
        valid = { comment =>
          val currentState = UserFsm(userId)
          (currentState ! RevokeApproval(userId, comment.text)).map { newState =>
            Logger.debug(s"state of user $userId changed from $currentState to $newState")
            Ok(success(Json.obj("state" -> newState)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not revoke approval for user $userId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing revoke approval request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "subscribeToNewsletter",
    value = "Subscribes a user to the newsletter",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing subscribe to newsletter request")))
  def subscribeToNewsletter(
    @ApiParam(
      name = "userId",
      value = "The id of the user to subscribe to the newsletter",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("subscribeToNewsletter") { token =>
    Action.async { implicit request =>
      userService.findAndUpdate(Id(Some(userId)).asJson, Json.obj("newsletter" -> true)).map {
        case Some(_) =>
          Logger.debug(s"user $userId subscribed to newsletter")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not subscribe user $userId to newsletter"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "unsubscribeFromNewsletter",
    value = "Unsubscribes a user from the newsletter",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing unsubscribe from newsletter request")))
  def unsubscribeFromNewsletter(
    @ApiParam(
      name = "userId",
      value = "The id of the user to unsubscribe from the newsletter",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("unsubscribeFromNewsletter") { token =>
    Action.async { implicit request =>
      userService.findAndUpdate(Id(Some(userId)).asJson, Json.obj("newsletter" -> JsNull)).map {
        case Some(_) =>
          Logger.debug(s"user $userId unsubscribed from newsletter")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not unsubscribe user $userId from newsletter"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "hide",
    value = "Hides a user from public",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing hide user request")))
  def hide(
    @ApiParam(
      name = "userId",
      value = "The id of the user to hide",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("hide") { token =>
    Action.async { implicit request =>
      userService.findAndUpdate(Id(Some(userId)).asJson, Json.obj("public" -> JsNull)).map {
        case Some(_) =>
          Logger.debug(s"hidden user $userId from public")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not hide user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "unhide",
    value = "Unhides a user from public",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing unhide user request")))
  def unhide(
    @ApiParam(
      name = "userId",
      value = "The id of the user to unhide",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized("unhide") { token =>
    Action.async { implicit request =>
      userService.findAndUpdate(Id(Some(userId)).asJson, Json.obj("public" -> true)).map {
        case Some(_) =>
          Logger.debug(s"unhidden user $userId from public")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not unhide user $userId"))
      }
    }
  }
}
