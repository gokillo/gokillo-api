/*#
  * @file Messages.scala
  * @begin 15-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.messaging

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import org.joda.time.{DateTime, DateTimeZone}
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors}
import services.auth.AuthErrors
import services.auth.UserDaoServiceComponent
import services.messaging.{ThreadDaoServiceComponent, MessageDaoServiceComponent}
import services.messaging.mongo.MongoMessageDaoComponent
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import utils.messaging.EmailHelper
import models.common.Id
import models.auth.{Role, Token}
import models.auth.TokenType.{Browse, Authorization => Auth, _}
import models.messaging.{Thread, Message}
import models.messaging.Message._

@Api(value = "/messaging/threads")
trait Messages extends Controller with Security {

  protected val errors: CommonErrors with DaoErrors with AuthErrors
  protected val threadService: ThreadDaoServiceComponent#ThreadDaoService
  protected val userService: UserDaoServiceComponent#UserDaoService

  protected val messageService: MessageDaoServiceComponent#MessageDaoService = new MessageDaoServiceComponent
    with MongoMessageDaoComponent {
  }.daoService.asInstanceOf[MessageDaoServiceComponent#MessageDaoService]

  protected def Authorized(
    threadId: String, operation: String, tokenTypes: TokenType*
  )(action: (Token, Thread) => EssentialAction): EssentialAction

  protected def Authorized(
    threadId: String, index: Int, operation: String, tokenTypes: TokenType*
  )(action: (Token, Thread) => EssentialAction): EssentialAction

  @ApiOperation(
    httpMethod = "POST",
    nickname = "createMessage",
    value = "Creates a new message in a thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id or invalid message data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 422, message = "Message data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing create message request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "message",
    value = "The message data",
    required = true,
    dataType = "models.messaging.api.Message",
    paramType = "body")))
  def createMessage(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread where to create the message",
      required = true)
    @PathParam("threadId")
    threadId: String
  ) = Authorized(threadId, "createMessage") { (token, thread) =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Message](messageFormat(Some(false))).fold(
        valid = { message =>
          threadService.incMessageCount(threadId, 1).flatMap { index =>
            message.threadId = Some(threadId)
            message.createdBy = Some(token.username)
            message.seqNumber = index
            messageService.insert(message).flatMap { newMessage =>
              val recipients = getRecipients(token.username, thread)
              userService.find(
                Json.obj("$in" -> Json.obj("username" -> Json.toJson(recipients))),
                Some(Json.obj("$include" -> Json.arr("email", "firstName", "lastName"))),
                None, 0, recipients.length
              ).map { users =>
                users.map { recipient =>
                  EmailHelper.sendNewPostInThreadEmail(recipient, thread, newMessage)
                }
                Logger.debug(s"${message.createdBy.get} created message ${index.get} in thread $threadId")
                Created(success).withHeaders(
                  LOCATION -> s"$requestUri/$threadId/messages/${index.get}"
                )
              }
            }
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create message in thread $threadId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create message request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateMessage",
    value = "Updates a message in a thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id or invalid message data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread or message not found"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update message request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "message",
    value = "The update data",
    required = true,
    dataType = "models.messaging.api.Message",
    paramType = "body")))
  def updateMessage(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread where to update the message",
      required = true)
    @PathParam("threadId")
    threadId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the message",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(threadId, index, "updateMessage") { (token, thread) =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Message](messageFormat(Some(true))).fold(
        valid = { update =>
          messageService.findAndUpdate(
            Json.obj("threadId" -> threadId, "seqNumber" -> index),
            update.asJson
          ).flatMap {
            case Some(oldMessage) =>
              val recipients = getRecipients(token.username, thread)
              userService.find(
                Json.obj("$in" -> Json.obj("username" -> Json.toJson(recipients))),
                Some(Json.obj("$include" -> Json.arr("email", "firstName", "lastName"))),
                None, 0, recipients.length
              ).map { users =>
                oldMessage.body = update.body
                users.map { recipient =>
                  EmailHelper.sendUpdateInThreadEmail(recipient, thread, oldMessage)
                }
                Logger.debug(s"updated message ${index} in thread $threadId")
                Ok(success)
              }
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound(s"message $index in thread", threadId), None))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update message $index in thread $threadId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update message request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteMessage",
    value = "Deletes a message in a thread",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread or message not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete message request")))
  def deleteMessage(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread where to delete the message",
      required = true)
    @PathParam("threadId")
    threadId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the message",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(threadId, index, "deleteMessage") { (token, thread) =>
    Action.async { implicit request =>
      messageService.findAndRemove(
        Json.obj("threadId" -> threadId, "seqNumber" -> index), None
      ).flatMap {
        case Some(removed) =>
          val recipients = getRecipients(token.username, thread)
          userService.find(
            Json.obj("$in" -> Json.obj("username" -> Json.toJson(recipients))),
            Some(Json.obj("$include" -> Json.arr("email", "firstName", "lastName"))),
            None, 0, recipients.length
          ).map { users =>
            users.map { recipient =>
              EmailHelper.sendRemovalInThreadEmail(recipient, thread, removed)
            }
            Logger.debug(s"deleted message $index in thread $threadId")
            Ok(success)
          }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound(s"message $index in thread", threadId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete message $index in thread $threadId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findMessage",
    value = "Finds a message in a thread",
    response = classOf[models.messaging.api.Message])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread or message not found"),
    new ApiResponse(code = 500, message = "Error processing find message request")))
  def findMessage(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread where to find the message",
      required = true)
    @PathParam("threadId")
    threadId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the message",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(threadId, "findMessage", Auth, Browse) { (token, _) =>
    Action.async { implicit request =>
      messageService.find(
        Json.obj("threadId" -> threadId, "seqNumber" -> index),
        Some(Json.obj("$exclude" -> Json.arr("seqNumber", "_version"))),
        None, 0, 1
      ).map { _.headOption match {
        case Some(message) => Ok(success(Json.obj("message" -> message.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound(s"message $index in thread", threadId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find message $index in thread $threadId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listMessages",
    value = "Lists the messages in a thread",
    response = classOf[models.messaging.api.Message],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid thread id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Thread not found"),
    new ApiResponse(code = 500, message = "Error processing list messages request")))
  def listMessages(
    @ApiParam(
      name = "threadId",
      value = "The id of the thread to list the messages in",
      required = true)
    @PathParam("threadId")
    threadId: String,
    
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
  ) = Authorized(threadId, "listMessages", Auth, Browse) { (token, _) =>
    Action.async { implicit request =>
      messageService.find(
        Json.obj("threadId" -> threadId),
        Some(Json.obj("$exclude" -> Json.arr("seqNumber", "_version"))),
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { messages =>
        if (messages.isEmpty) errors.toResult(CommonErrors.EmptyList("messages", "thread", threadId), None)
        else Ok(success(Json.obj("messages" -> Json.toJson(messages))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list messages in thread $threadId"))
      }
    }
  }

  /**
    * Returns the recipients of a notification about an event in the
    * specified thread.
    *
    * @param username The username of the user that triggered the event in `thread`.
    * @param thread   The thread in which the event occurred.
    * @return         The recipients of the notification.
    */
  protected def getRecipients(username: String, thread: Thread): List[String] =  {
    val createdBy = thread.createdBy.getOrElse("")
    thread.grantees match {
      case Some(grantees) if grantees.contains(username) => createdBy :: grantees.filterNot(_ == username)
      case Some(grantees) => grantees
      case _ => List[String]()
    }
  }
}
