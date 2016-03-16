/*#
  * @file FsController.scala
  * @begin 8-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.common

import scala.concurrent.Future
import scala.util.control.{ControlThrowable, NonFatal}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.json.JsValue
import controllers.common.ContentDisposition._
import services.common.FsServiceComponent
import models.common.{MetaFile, ByteRange}

/**
  * Provides application controllers with file store support.
  */
trait FsController { self: Controller =>

  private class RangeNotSatisfiable extends ControlThrowable

  /**
    * Serves the specified file from the current file store.
    *
    * @param file       The file to serve.
    * @param contentDisposition One of the [[ContentDisposition]] values.
    * @param fsService  The file store service.
    * @param request    The current HTTP request header.
    * @return           A `Future` value containing the content of `file` as
    *                   a `Result`.
    */
  def serveFile(file: MetaFile, contentDisposition: Option[ContentDisposition] = None)(
    implicit fsService: FsServiceComponent#FsService, request: RequestHeader
  ) = Future {
    import scala.collection.mutable.Map
    import play.api.libs.iteratee.Enumerator
    import utils.common.typeExtensions._

    val filename = file.filename
    val length = file.length

    val cd = contentDisposition getOrElse { request.getQueryString("inline") match {
      case Some("true") => ContentDisposition.Inline
      case _ => ContentDisposition.Attachment
    }}

    val headers: Map[String, String] = Map(
      DATE -> DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZoneUTC().print(DateTime.now),
      ACCEPT_RANGES -> "bytes",
      CONTENT_DISPOSITION -> (s"""$cd; filename="$filename"; filename*=UTF-8''"""
        + java.net.URLEncoder.encode(filename, "UTF-8").replace("+", "%20")),
      CONTENT_TYPE -> file.contentType.getOrElse("application/octet-stream")
    )

    val (status: Int, body: Enumerator[Array[Byte]]) = request.headers.get(RANGE).map { header =>
      try {
        val range = ByteRange(header.splitAfter("=")._2, length - 1)
        if (range.last >= length) throw new RangeNotSatisfiable
        headers += (
          CONTENT_RANGE -> s"bytes ${range.first}-${range.last}/$length",
          CONTENT_LENGTH -> s"${range.length}"
        )
        (PARTIAL_CONTENT, fsService.enumerate(file, Some(range)))
      } catch { case NonFatal(_) | _: RangeNotSatisfiable => 
        headers += (CONTENT_RANGE -> s"bytes */$length")
        (REQUESTED_RANGE_NOT_SATISFIABLE, Enumerator.empty)
      }
    } getOrElse (OK, fsService.enumerate(file))

    Result(ResponseHeader(status, headers.toMap), body)
  }

  /**
    * Returns a body parser that will save a file sent with multipart/form-data
    * into a file store.
    *
    * @param fsService  The file store service that persists the file.
    * @return           A body parser that will save a file into the file store.
    */
  def fsBodyParser()(
    implicit fsService: FsServiceComponent#FsService
  ): BodyParser[MultipartFormData[Future[MetaFile]]] = {
    import BodyParsers.parse._
    import org.apache.commons.lang.RandomStringUtils

    multipartFormData(
      Multipart.handleFilePart {
        case Multipart.FileInfo(partName, filename, contentType) =>
          val randomString = RandomStringUtils.randomAlphanumeric(8)
          val randomFilename = filename.split("\\.") match {
            case array if array.length > 1 => s"$randomString.${array(array.length - 1)}"
            case _ => randomString
          }
          fsService.iteratee(randomFilename, contentType)
      }
    )
  }
}
