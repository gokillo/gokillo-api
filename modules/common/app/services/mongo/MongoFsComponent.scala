/*#
  * @file MongoFsComponent.scala
  * @begin 11-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

import scala.concurrent.Future
import scala.util.Failure
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.core.commands._
import reactivemongo.api.gridfs._
import reactivemongo.bson._
import services.common._
import models.common.{Id, MetaFile, ByteRange}

/**
  * Defines functionality for storing and retrieving files.
  */
trait MongoFsComponent extends FsComponent with FieldMapping {

  private val gridFs = GridFS(ReactiveMongoPlugin.db, namespace)
  gridFs.ensureIndex()

  defaultMaps = defaultMaps ++ Map(
    "uploadDate" -> ("uploadDate", Some("$date"))
  )

  /**
    * Gets namespace of the file store.
    * @return The namespace of the file store.
    */
  protected def namespace: String

  /**
    * Gets an instance of the [[Fs]] implementation for Mongo.
    * @return An instance of the [[Fs]] implementation for Mongo.
    */
  def fs = new MongoFs

  /**
    * Implements a file store for Mongo.
    */
  class MongoFs extends Fs {

    import play.api.libs.json._
    import bsonFormatters._
    import typeExtensions._

    def count(selector: JsValue): Future[Int] = {
      val _selector = if (selector != null) selector.toSelector else Json.obj()
      validateFields(_selector) match {
        case Failure(e) => Future.failed(e)
        case _ => ReactiveMongoPlugin.db.command(Count(
          s"$namespace.files",
          Some(_selector.toBson)
        )).recover {
          case e: LastError => throw DaoErrors.DatabaseError(s"$namespace.files", e)
        }
      }
    }

    def save(filename: String, contentType: Option[String], provider: Enumerator[Array[Byte]]): Future[MetaFile] = {
      import reactivemongo.api.gridfs.Implicits.DefaultReadFileReader

      gridFs.save(provider, DefaultFileToSave(filename, contentType)).map {
        case file => MetaFile(file.asInstanceOf[DefaultReadFile].original.toJson.toPublic)
      }.recover {
        case e: LastError => throw DaoErrors.DatabaseError(s"$namespace.chunks", e)
      }
    }

    def update(id: Id, metadata: JsValue): Future[Boolean] = {
      update(id.asJson, metadata).map(_ > 0)
    }

    def update(selector: JsValue, metadata: JsValue): Future[Int] = {
      if (selector == null || selector.as[JsObject].values.isEmpty) {
        Future.failed(DaoErrors.NoData(s"$namespace.files", "selector"))
      } else if (metadata == null || metadata.as[JsObject].values.isEmpty) {
        Future.failed(DaoErrors.NoData(s"$namespace.files", "metadata"))
      } else validateFields(selector, metadata) match {
        case Failure(e) => Future.failed(e)
        case _ => gridFs.files.update(
            selector.toSelector.toBson,
            metadata.toUpdate.toBson,
            multi = true
          ).map(_.n).recover { case e: LastError => e.code match {
            case Some(11000) => throw DaoErrors.DuplicateKey(e.message.errorField.getOrElse("?"), s"$namespace.files")
            case _ => throw DaoErrors.DatabaseError(s"$namespace.files", e)
         }}
      }
    }

    def remove(file: MetaFile): Future[Boolean] = {
      gridFs.remove(file.asJson.fromPublic.toBson).map(_.n > 0).recover {
        case e: LastError => throw DaoErrors.DatabaseError(s"$namespace.*", e)
      }
    }

    def remove(id: Id): Future[Boolean] = {
      remove(id.asJson).map(_ > 0)
    }

    def remove(selector: JsValue): Future[Int] = {
      import reactivemongo.api.gridfs.Implicits.DefaultReadFileReader

      if (selector == null || selector.as[JsObject].values.isEmpty) {
        Future.failed(DaoErrors.NoData(s"$namespace.*", "selector"))
      } else validateFields(selector) match {
        case Failure(e) => Future.failed(e)
        case _ => gridFs.find(selector.toSelector.toBson).collect[Seq]().flatMap { files =>
          Future.successful(files.map(gridFs.remove(_)))
        }.map(_.length).recover {
          case e: LastError => throw DaoErrors.DatabaseError(s"$namespace.*", e)
        }
      }
    }

    def enumerate(file: MetaFile, range: Option[ByteRange] = None): Enumerator[Array[Byte]] = {
      import reactivemongo.bson.DefaultBSONHandlers._
      import reactivemongo.api.collections.default.BSONCollection

      val chunkSize = file.chunkSize
      val byteRange = range.getOrElse(ByteRange(0, file.length - 1))

      val selector = BSONDocument(
        "$query" -> BSONDocument(
          "files_id" -> BSONObjectID(file.id),
          "n" -> BSONDocument(
            "$gte" -> BSONInteger(byteRange.first / chunkSize),
            "$lte" -> BSONInteger(byteRange.last / chunkSize)
          )
        ),
        "$orderby" -> BSONDocument("n" -> BSONInteger(1))
      )

      var read = 0
      val cursor = gridFs.chunks.as[BSONCollection]().find(selector).cursor

      cursor.enumerate() &> Enumeratee.map { doc =>
        doc.get("data").flatMap {
          case BSONBinary(data, _) =>
            val discardable = if (read == 0) byteRange.first % data.readable else {
              if (read + data.readable > byteRange.length) data.readable - (byteRange.last % data.readable)
              else 0
            }
            val array = new Array[Byte](data.readable - discardable)
            if (read == 0 && discardable > 0) data.discard(discardable)
            data.slice(array.length).readBytes(array)
            read += array.length
            Some(array)
          case _ => None
        } getOrElse {
          throw DaoErrors.DatabaseError(s"$namespace.chunks", new Exception("no chunk data to enumerate"))
        }
      }
    }

    def iteratee(filename: String, contentType: Option[String]): Iteratee[Array[Byte], Future[MetaFile]] = {
      import reactivemongo.api.gridfs.Implicits.DefaultReadFileReader

      gridFs.iteratee(DefaultFileToSave(filename, contentType)).map { _.map {
        case file => MetaFile(file.asInstanceOf[DefaultReadFile].original.toJson.toPublic)
      }}
    }

    def find(id: Id): Future[Option[MetaFile]] = {
      find(id.asJson).map {
        case seq if seq.nonEmpty => Some(seq.head)
        case _ => None
      }
    }

    def find(selector: JsValue): Future[Seq[MetaFile]] = {
      import reactivemongo.api.gridfs.Implicits.DefaultReadFileReader

      val _selector = if (selector != null) selector else Json.obj()
      validateFields(_selector) match {
        case Failure(e) => Future.failed(e)
        case _ => gridFs.find(_selector.toSelector.toBson).collect[Seq]().map {
            case seq if seq.nonEmpty =>
              for (file <- seq) yield MetaFile(file.asInstanceOf[DefaultReadFile].original.toJson.toPublic)
            case _ => Seq[MetaFile]()
          }.recover {
            case e: LastError => throw DaoErrors.DatabaseError(s"$namespace.files", e)
          }
      }
    }
  }
}
