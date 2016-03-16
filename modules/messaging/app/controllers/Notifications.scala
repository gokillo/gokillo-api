/*#
  * @file Notifications.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.messaging

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import org.joda.time.{DateTime, DateTimeZone}
import play.api._
import play.api.Play.current
import play.api.Play.configuration
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.twirl.api.Html
import controllers.auth.Security
import services.common._
import services.auth.{AuthErrors, UserDaoServiceComponent}
import services.auth.mongo.MongoUserDaoComponent
import services.messaging.{MessagingErrors, NotificationDaoServiceComponent}
import services.messaging.mongo.MongoNotificationDaoComponent
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.common.{Contact, Id}
import models.messaging.Notification
import models.messaging.Notification._

@Api(
  value = "/messaging/notifications",
  description = "Create, manage, and send notifications",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Notifications extends Controller with Security {

  private final val RecipientWildcard = "*"

  protected val errors = new CommonErrors with DaoErrors with AuthErrors with MessagingErrors {}

  protected val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
    with MongoUserDaoComponent {
  }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

  protected val notificationService: NotificationDaoServiceComponent#NotificationDaoService = new NotificationDaoServiceComponent
    with MongoNotificationDaoComponent {
  }.daoService.asInstanceOf[NotificationDaoServiceComponent#NotificationDaoService]

  protected val emailService: EmailServiceComponent#EmailService = new DefaultEmailServiceComponent
    with DefaultEmailComponent {
  }.emailService

  @ApiOperation(
    httpMethod = "POST",
    nickname = "create",
    value = "Creates a new notification",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid notification data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "Notification data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing create notification request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "notification",
    value = "The notification data",
    required = true,
    dataType = "models.messaging.api.Notification",
    paramType = "body")))
  def create = Authorized("create") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Notification](notificationFormat(Some(false))).fold(
        valid = { notification =>
          notificationService.insert(notification).flatMap { newNotification =>
            Logger.debug(s"created notification ${newNotification.id.get}")
            Future.successful(Created(success).withHeaders(
              LOCATION -> s"$requestUri/${newNotification.id.get}"
            ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not create notification"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create notification request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "update",
    value = "Updates a notification",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid notification id or invalid notification data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Notification not found or already sent"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing update notification request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "notification",
    value = "The update data",
    required = true,
    dataType = "models.messaging.api.Notification",
    paramType = "body")))
  def update(
    @ApiParam(
      name = "notificationId",
      value = "The id of the notification to update",
      required = true)
    @PathParam("notificationId")
    notificationId: String
  ) = Authorized("update") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Notification](notificationFormat(Some(true))).fold(
        valid = { update =>
          notificationService.find(
            notificationId,
            Some(Json.obj("$exclude" -> Json.arr("subject", "body")))
          ).flatMap {
            case Some(notification) => notification.sentTime match {
              case None => notificationService.findAndUpdate(Id(Some(notificationId)).asJson, update.asJson).map { _ =>
                Logger.debug(s"updated notification $notificationId")
                Ok(success)
              }
              case _ => Future.successful(errors.toResult(
                CommonErrors.NotAllowed("notification", "update", s"notification $notificationId already sent"),
                None
              ))
            }
            case _ => Future.successful(errors.toResult(CommonErrors.NotFound("notification", notificationId), None))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not update notification $notificationId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update notification request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "delete",
    value = "Deletes a notification",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid notification id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Notification not found"),
    new ApiResponse(code = 500, message = "Error processing delete notification request")))
  def delete(
    @ApiParam(
      name = "notificationId",
      value = "The id of the notification to delete",
      required = true)
    @PathParam("notificationId")
    notificationId: String
  ) = Authorized("delete") { token =>
    Action.async { implicit request =>
      notificationService.findAndRemove(notificationId).map {
        case Some(removedNotification) =>
          Logger.debug(s"deleted notification ${removedNotification.id.get}")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("notification", notificationId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete notification $notificationId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds a notification",
    response = classOf[models.messaging.api.Notification])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid notification id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Notification not found"),
    new ApiResponse(code = 500, message = "Error processing find notification request")))
  def find(
    @ApiParam(
      name = "notificationId",
      value = "The id of the notification to find",
      required = true)
    @PathParam("notificationId")
    notificationId: String
  ) = Authorized("find") { token =>
    Action.async { implicit request =>
      notificationService.find(
        notificationId,
        Some(Json.obj("$exclude" -> Json.arr("_version")))
      ).map {
        case Some(notification) => Ok(success(Json.obj("notification" -> notification.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("notification", notificationId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find notification $notificationId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the notifications",
    response = classOf[models.messaging.api.Notification],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No notifications found"),
    new ApiResponse(code = 500, message = "Error processing list notifications request")))
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
      notificationService.find(
        Json.obj(),
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { notifications =>
        if (notifications.isEmpty) errors.toResult(CommonErrors.EmptyList("notifications"), None)
        else Ok(success(Json.obj("notifications" -> Json.toJson(notifications))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list notifications"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "send",
    value = "Sends a notification",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid notification id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing send notification request")))
  def send(
    @ApiParam(
      name = "notificationId",
      value = "The id of the notification to send",
      required = true)
    @PathParam("notificationId")
    notificationId: String
  ) = Authorized("send") { token =>
    Action.async { implicit request =>
      def sendToMembers(notification: Notification, page: Int, count: Int): Future[Int] = {
        for {
          users <- userService.find(
            Json.obj("public" -> true),
            Some(Json.obj("$include" -> Json.arr("email"))),
            None, page, 100
          )
          sent <- users.isEmpty match {
            case true => Future.successful(count)
            case false => for {
              _ <- emailService.sendEmail(
                None, // default sender
                users.map(_.email.get),
                notification.subject.getOrElse(""),
                Html(notification.body.getOrElse(""))
              )
              // invoke sendToMembers recursively until there are no users left
              sent <- sendToMembers(notification, page + 1, count + users.length)
            } yield sent
          }
        } yield sent
      }

      notificationService.find(
        notificationId,
        Some(Json.obj("$exclude" -> Json.arr("_version")))
      ).flatMap {
        case Some(notification) => notification.sentTime match {
          case None => notificationService.findAndUpdate(
            Id(Some(notificationId)).asJson,
            Notification(sentTime = Some(DateTime.now(DateTimeZone.UTC))).asJson
          ).flatMap { _ =>
            val recipients = notification.recipients.getOrElse(List.empty).filter(_ != RecipientWildcard)
            val sendEmailFuture = if (recipients.nonEmpty) emailService.sendEmail(
              None, // default sender
              recipients,
              notification.subject.getOrElse(""),
              Html(notification.body.getOrElse(""))
            ) else Future.successful(Unit)
            sendEmailFuture.flatMap { _ =>
              { notification.recipients.get.contains(RecipientWildcard) match {
                case true => sendToMembers(notification, 0, 0)
                case false => Future.successful(0)
              }}.map { count =>
                val total = recipients.length + count
                Logger.debug(s"""sent notification $notificationId to $total recipient${if (total != 1) "s" else ""}""")
                Ok(success)
              }
            }
          }
          case _ => Future.successful(errors.toResult(MessagingErrors.AlreadySent("notification", notificationId), None))
        }
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("notification", notificationId), None))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not send notification $notificationId"))
      }
    }
  }
}
