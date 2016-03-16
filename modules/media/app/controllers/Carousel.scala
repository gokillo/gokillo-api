/*#
  * @file Carousel.scala
  * @begin 10-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.media

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
import services.common.{CommonErrors, DaoErrors, FsServiceComponent, DefaultFsServiceComponent}
import services.auth.AuthErrors
import services.media.mongo._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.auth.TokenType.{Browse, Authorization => Auth}

@Api(
  value = "/media/carousel",
  description = "Manage carousel media",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Carousel extends Controller with Security with FsController {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors {}

  protected implicit val fsService: FsServiceComponent#FsService = new DefaultFsServiceComponent
    with MongoCarouselFsComponent {
  }.fsService

  @ApiOperation(
    httpMethod = "POST",
    nickname = "saveMedia",
    value = "Saves a media into the carousel",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing save media request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "media",
    value = "The media to save",
    required = true,
    dataType = "file",
    paramType = "body")))
  def saveMedia(
    @ApiParam(
      name = "label",
      value = "The label to be associated with the media",
      required = true)
    @PathParam("label")
    label: String
  ) = Authorized("saveMedia") { token =>
    Action.async(fsBodyParser) { implicit request =>
      val result = for {
        file <- request.body.files.head.ref
        update <- {
          fsService.update(
            file.id,
            Json.obj("metadata" -> Json.obj("label" -> label))
          )
        }
      } yield update

      result.map { _ =>
        Logger.debug(s"saved media into carousel")
        Created(success).withHeaders(
          LOCATION -> requestUri
        )
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not save media"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listMedia",
    value = "Lists all the media in the carousel",
    response = classOf[models.common.api.MetaFile],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing list media request")))
  def listMedia = Authorized("listMedia", Auth, Browse) { token =>
    Action.async { implicit request =>
      fsService.find(Json.obj()).map {
        case media if media.nonEmpty => Ok(success(Json.obj("media" -> Json.toJson(media))))
        case _ => errors.toResult(CommonErrors.EmptyList("media"), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list media"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteMedia",
    value = "Deletes a media in the carousel",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Media not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete media request")))
  def deleteMedia(
    @ApiParam(
      name = "mediaId",
      value = "The id of the media to delete",
      required = true)
    @PathParam("mediaId")
    mediaId: String
  ) = Authorized("deleteMedia") { token =>
    Action.async { implicit request =>
      fsService.remove(Json.obj("id" -> mediaId)).map {
        case n if n > 0 =>
          Logger.debug(s"deleted media $mediaId in carousel")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("media", mediaId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete media $mediaId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getMedia",
    value = "Gets a media in the carousel",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid media id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Media not found"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 500, message = "Error processing get media request")))
  def getMedia(
    @ApiParam(
      name = "mediaId",
      value = "The id of the media to get",
      required = true)
    @PathParam("mediaId")
    mediaId: String
  ) = Authorized("getMedia", Auth, Browse) { token =>
    Action.async { implicit request =>
      fsService.find(Json.obj("id" -> mediaId)).flatMap { _.headOption match {
        case Some(avatar) => serveFile(avatar)
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("media", mediaId), None))
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not get media $mediaId"))
      }
    }
  }
}
