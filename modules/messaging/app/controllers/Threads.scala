/*#
  * @file Threads.scala
  * @begin 15-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.messaging

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors}
import services.auth.AuthErrors
import services.auth.UserDaoServiceComponent
import services.auth.mongo.MongoUserDaoComponent
import services.messaging.ThreadDaoServiceComponent
import services.messaging.mongo.MongoThreadDaoComponent
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.common.{Id, RefId}
import models.common.RefId._
import models.auth.{Role, Token}
import models.auth.TokenType.{Browse, Authorization => Auth, _}
import models.messaging.Thread
import models.messaging.Thread._

@Api(
  value = "/messaging/threads",
  description = "Create message threads and view their history",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Threads extends Controller with Security with Messages {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors {}

  protected val threadService: ThreadDaoServiceComponent#ThreadDaoService = new ThreadDaoServiceComponent
    with MongoThreadDaoComponent {
  }.daoService.asInstanceOf[ThreadDaoServiceComponent#ThreadDaoService]

  protected val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
    with MongoUserDaoComponent {
  }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

  /**
    * Creates an action that authorizes the specified operation only if issued
    * by the user that owns the specified thread.
    *
    * @param threadId   The identifier of the thread.
    * @param operation  The name of the operation to authorize.
    * @param tokenTypes A sequence of `TokenType` values.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  protected def OwnerAuthorized(
    threadId: String, operation: String, tokenTypes: TokenType*
  )(action: Token => EssentialAction): EssentialAction = Authorized(operation, tokenTypes: _*) { token =>
    EssentialAction { implicit request =>
      Iteratee.flatten(
        threadService.find(
          threadId,
          Some(Json.obj("$include" -> Json.arr("createdBy")))
        ).map {
          case Some(thread) if thread.createdBy.get == token.username || token.roles.contains(Role.Superuser.id) => action(token)(request)
          case Some(thread) => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.NotAuthorized(
                "operation", operation, "user", token.username,
                s"user ${token.username} is not the owner of thread $threadId"
              ),
              Some(s"request $requestUriWithMethod not authorized")
            ))
          case _ => Done[Array[Byte], Result](errors.toResult(CommonErrors.NotFound("thread", threadId), None))
        }
      )
    }
  }

  /**
    * Creates an action that authorizes the specified operation only if the specified
    * thread is not confidential or the user is allowed to participate in the thread.
    *
    * @param threadId   The identifier of the thread.
    * @param operation  The name of the operation to authorize.
    * @param tokenTypes A sequence of `TokenType` values.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  protected def Authorized(
    threadId: String, operation: String, tokenTypes: TokenType*
  )(action: (Token, Thread) => EssentialAction): EssentialAction = Authorized(operation, tokenTypes: _*) { token =>
    EssentialAction { implicit request =>
      Iteratee.flatten(
        threadService.find(
          threadId,
          Some(Json.obj("$exclude" -> Json.arr("_version")))
        ).map {
          case Some(thread) =>
            val grantees: List[String] = thread.grantees match {
              case Some(grantees) if grantees.nonEmpty => grantees
              case _ => List.empty
            }

            if (!thread.confidential || thread.createdBy.get == token.username || grantees.contains((token.username)) || token.roles.contains(Role.Superuser.id))
              action(token, thread)(request)
            else Done[Array[Byte], Result](errors.toResult(
              AuthErrors.NotAuthorized(
                "operation", operation, "user", token.username,
                s"user ${token.username} is not allowed to participate in thread $threadId"
              ),
              Some(s"request $requestUriWithMethod not authorized")
            ))
          case _ => Done[Array[Byte], Result](errors.toResult(CommonErrors.NotFound("thread", threadId), None))
        }
      )
    }
  }

  /**
    * Creates an action that authorizes the specified operation only if issued
    * by the user that created the specified message.
    *
    * @param threadId   The identifier of the thread the message belongs to.
    * @param index      The zero-based index of the message.
    * @param operation  The name of the operation to authorize.
    * @param tokenTypes A sequence of `TokenType` values.
    * @param action     A function that takes a `Token` and returns an `EssentialAction`.
    * @return           An authorized action.
    */
  protected def Authorized(
    threadId: String, index: Int, operation: String, tokenTypes: TokenType*
  )(action: (Token, Thread) => EssentialAction): EssentialAction = Authorized(threadId, operation, tokenTypes: _*) { (token, thread) =>
    EssentialAction { implicit request =>
      Iteratee.flatten(
        messageService.find(
          Json.obj("threadId" -> threadId, "seqNumber" -> index),
          Some(Json.obj("$include" -> Json.arr("createdBy"))),
          None, 0, 1
        ).map { _.headOption match {
          case Some(message) if message.createdBy.get == token.username || token.roles.contains(Role.Superuser.id) => action(token, thread)(request)
          case Some(_) => Done[Array[Byte], Result](errors.toResult(
              AuthErrors.NotAuthorized(
                "operation", operation, "user", token.username,
                s"user ${token.username} is not the creator of message $index in thread $threadId"
              ),
              Some(s"request $requestUriWithMethod not authorized")
            ))
          case _ => Done[Array[Byte], Result](errors.toResult(CommonErrors.NotFound(s"message $index in thread", threadId), None))
        }}
      )
    }   
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "create",
    value = "Creates a new message thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "Thread data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing create thread request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "thread",
    value = "The thread data",
    required = true,
    dataType = "models.messaging.api.Thread",
    paramType = "body")))
  def create = Authorized("create") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Thread](threadFormat(Some(false))).fold(
        valid = { thread =>
          thread.createdBy = Some(token.username)
          thread.messageCount = Some(0)
          threadService.insert(thread).flatMap { newThread =>
            Logger.debug(s"created thread ${newThread.id.get}")
            Future.successful(Created(success).withHeaders(
              LOCATION -> s"$requestUri/${newThread.id.get}"
            ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not create thread"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create thread request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "update",
    value = "Updates a thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id or invalid thread data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing update thread request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "thread",
    value = "The update data",
    required = true,
    dataType = "models.messaging.api.Thread",
    paramType = "body")))
  def update(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to update",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = OwnerAuthorized(threadId, "update") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Thread](threadFormat(Some(true))).fold(
        valid = { update =>
          // prevent thread confidentiality from being modified
          if (update.confidentialOpt.isDefined) {
            Logger.warn(s"confidentiality of thread $threadId was set for update: ignored")
            update.confidentialOpt = None
          }

          // prevent grantees from being updated here; use specific api instead
          if (update.grantees.isDefined) {
            Logger.warn(s"grantees of thread $threadId were set for update: ignored")
            update.grantees = None
          }

          // prevent message count from being modified
          if (update.messageCount.isDefined) {
            Logger.warn(s"message count of thread $threadId was set for update: ignored")
            update.messageCount = None
          }

          threadService.findAndUpdate(Id(Some(threadId)).asJson, update.asJson).map {
            case Some(_) =>
              Logger.debug(s"updated thread $threadId")
              Ok(success)
            case _ => errors.toResult(CommonErrors.NotFound("thread", threadId), None)
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update thread $threadId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update thread request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "delete",
    value = "Deletes a thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 500, message = "Error processing delete thread request")))
  def delete(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to delete",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = OwnerAuthorized(threadId, "delete") { token =>
    Action.async { implicit request =>
      (threadService.findAndRemove(threadId) zip messageService.remove(Json.obj("threadId" -> threadId))).map {
        case (Some(_), _) =>
          Logger.debug(s"deleted thread $threadId")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("thread", threadId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete thread $threadId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds a thread",
    response = classOf[models.messaging.api.Thread])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 500, message = "Error processing find thread request")))
  def find(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to find",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = Authorized(threadId, "find", Auth, Browse) { (token, thread) =>
    Action.async { implicit request =>
      thread.grantees = None
      Future.successful(Ok(success(Json.obj("thread" -> thread.asJson))))
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "grantMembership",
    value = "Grants membership in a thread to one or more users",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id or invalid username list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread or users not found"),
    new ApiResponse(code = 422, message = "Username list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing grant membership request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "usernames",
    value = "The username of the users to grant membership in the thread",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def grantMembership(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to grant membership in",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = OwnerAuthorized(threadId, "grantMembership") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { usernames =>
          // filter out thread owner, if present
          val _usernames = usernames.filterNot(_ == token.username)
          userService.find(
            Json.obj("$in" -> Json.obj("username" -> Json.toJson(_usernames))),
            Some(Json.obj("$include" -> Json.arr("id", "username"))),
            None, 0, _usernames.length
          ).flatMap { users =>
            _usernames.filterNot(users.map(user => user.username.get).toSet) match {
              case missing if missing.nonEmpty => Future.successful(
                errors.toResult(CommonErrors.NotFound("users", missing.mkString(", ")), None)
              )
              case _ => threadService.addGrantees(threadId, _usernames).map { _ => Ok(success) }
            }
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing grant membership request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "revokeMembership",
    value = "Revokes membership in a thread to one or more users",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id or invalid username list"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread or users not found"),
    new ApiResponse(code = 422, message = "Username list with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing revoke membership request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "usernames",
    value = "The username of the users to revoke membership in the thread",
    required = true,
    dataType = "List[String]",
    paramType = "body")))
  def revokeMembership(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to revoke membership in",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = OwnerAuthorized(threadId, "revokeMembership") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[List[String]].fold(
        valid = { usernames =>
          // filter out thread owner, if present
          val _usernames = usernames.filterNot(_ == token.username)
          userService.find(
            Json.obj("$in" -> Json.obj("username" -> Json.toJson(_usernames))),
            Some(Json.obj("$include" -> Json.arr("id", "username"))),
            None, 0, _usernames.length
          ).flatMap { users =>
            _usernames.filterNot(users.map(user => user.username.get).toSet) match {
              case missing if missing.nonEmpty => Future.successful(
                errors.toResult(CommonErrors.NotFound("users", missing.mkString(", ")), None)
              )
              case _ => threadService.removeGrantees(threadId, _usernames).map { _ => Ok(success) }
            }
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing revoke membership request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getMessageCount",
    value = "Gets the message count of a given thread, regardless of whether it is public or confidential",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 500, message = "Error processing get message count request")))
  def getMessageCount(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to get the message count for",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = Authorized(threadId, "getMessageCount", Auth, Browse) { (token, thread) =>
    Action.async { implicit request =>
      Future.successful(Ok(success(Json.obj("messageCount" -> thread.messageCount))))
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the public threads",
    response = classOf[models.messaging.api.Thread],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No threads found"),
    new ApiResponse(code = 500, message = "Error processing list threads request")))
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
      threadService.find(
        Json.obj("$ne" -> Json.obj("confidential" -> true)),
        Some(Json.obj("$exclude" -> Json.arr("grantees", "_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { threads =>
        if (threads.isEmpty) errors.toResult(CommonErrors.EmptyList("threads"), None)
        else Ok(success(Json.obj("threads" -> Json.toJson(threads))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list public threads"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listConfidential",
    value = "Lists the confidential threads of a user",
    response = classOf[models.messaging.api.Thread],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No threads found"),
    new ApiResponse(code = 500, message = "Error processing list confidential threads request")))
  def listConfidential(
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
  ) = Authorized("listConfidential") { token =>
    Action.async { implicit request =>
      threadService.find(
        Json.obj("confidential" -> true, "$or" -> Json.arr(Json.obj("createdBy" -> token.username), Json.obj("grantees" -> token.username))),
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { threads =>
        if (threads.isEmpty) errors.toResult(CommonErrors.EmptyList("threads"), None)
        else Ok(success(Json.obj("threads" -> Json.toJson(threads))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list confidential threads of user ${token.username}"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "listByRefId",
    value = "Lists the public threads associated with an object in a specific domain",
    response = classOf[models.messaging.api.Thread],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid reference id data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No threads found"),
    new ApiResponse(code = 422, message = "Reference id data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing list threads by reference id request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "refId",
    value = "The reference id data",
    required = true,
    dataType = "models.common.api.RefId",
    paramType = "body")))
  def listByRefId(
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
  ) = Authorized("listByRefId", Auth, Browse) { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[RefId](refIdFormat).fold(
        valid = { refId =>
          threadService.find(
            Json.obj(
              "$ne" -> Json.obj("confidential" -> true),
              "refId.domain" -> refId.domain, "refId.name" -> refId.name, "refId.value" -> refId.value
            ),
            Some(Json.obj("$exclude" -> Json.arr("grantees", "_version"))),
            Some(Json.obj("$desc" -> Json.arr("creationTime"))),
            page, perPage
          ).map { threads =>
            if (threads.isEmpty) errors.toResult(CommonErrors.EmptyList("threads"), None)
            else Ok(success(Json.obj("threads" -> Json.toJson(threads))))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not list threads by reference id"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing list threads by reference id request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "count",
    value = "Counts all the public threads",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing count threads request")))
  def count(
    @ApiParam(
      name = "domain",
      value = "The domain to count the threads for",
      required = false)
    @PathParam("domain")
    domain: String
  ) = Authorized("count", Auth, Browse) { token =>
    Action.async { implicit request =>
      val selector = Json.obj("$ne" -> Json.obj("confidential" -> true))
      threadService.count(
        if (domain != null) selector ++ Json.obj("refId.domain" -> domain) else selector
      ).map { n =>
        Ok(success(Json.obj("threadCount" -> n)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not count threads"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "countConfidential",
    value = "Counts the confidential threads of a user",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing count confidential threads request")))
  def countConfidential = Authorized("countConfidential", Auth) { token =>
    Action.async { implicit request =>
      threadService.count(
        Json.obj("createdBy" -> token.username, "confidential" -> true)
      ).map { n =>
        Ok(success(Json.obj("threadCount" -> n)))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not count confidential threads"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "countByRefId",
    value = "Counts the public threads associated with an object in a specific domain",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid reference id data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "Reference id data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing count threads by reference id request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "refId",
    value = "The reference id data",
    required = true,
    dataType = "models.common.api.RefId",
    paramType = "body")))
  def countByRefId = Authorized("countByRefId", Auth, Browse) { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[RefId](refIdFormat).fold(
        valid = { refId =>
          threadService.count(
            Json.obj(
              "$ne" -> Json.obj("confidential" -> true),
              "refId.domain" -> refId.domain, "refId.name" -> refId.name, "refId.value" -> refId.value
            )
          ).map { n =>
            Ok(success(Json.obj("threadCount" -> n)))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not count threads by reference id"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing count threads by reference id request")))
        }
      )
    }
  }
}
