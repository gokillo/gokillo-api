/*#
  * @file MongoDaoComponent.scala
  * @begin 17-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

import scala.concurrent.Future
import scala.util.Failure
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.data.validation.ValidationError
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.ReactiveMongoPlugin
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.core.commands._
import services.common._
import models.common.{Id, JsEntity}

/**
  * Defines functionality for Mongo data access objects.
  * @tparam A The type this `MongoDaoComponent` provides accesss for.
  */
trait MongoDaoComponent[A <: JsEntity] extends DaoComponent[A] with FieldMapping {

  /**
    * The collection that holds MongoDB data.
    */
  protected val collection: JSONCollection

  /**
    * Gets an instance of the [[Dao]] implementation for Mongo.
    * @return An instance of the [[Dao]] implementation for Mongo.
    */
  def dao = new MongoDao

  /**
    * Implements data access object for Mongo.
    */
  class MongoDao extends Dao {

    import typeExtensions._

    def collectionName = collection.name

    def generateId: String = BSONObjectID.generate.stringify

    def count(selector: JsValue): Future[Int] = {
      db.count(selector.toSelector)
    }

    def distinct(field: String, selector: JsValue): Future[Seq[String]] = {
      db.distinct(if (field == "id") "_id" else field, selector.toSelector)
    }

    def insert(entity: A): Future[A] = {
      import _root_.utils.common.Formats._

      if (!entity.creationTime.isDefined) entity.creationTime = Some(DateTime.now(DateTimeZone.UTC))
      db.insert(entity.asJson.fromPublic.withObjectId.setVersion(Some(1))).map { created =>
        box(created.toPublic)
      }
    }

    def update(entity: A): Future[Boolean] = {
      entity.id match {
        case Some(id) => update(Json.obj("id" -> id), entity.asJson.as[JsObject] - "id" - "_version").map (_ > 0)
        case None => Future.failed(DaoErrors.MissingField("id", collectionName))
      }
    }

    def update(selector: JsValue, update: JsValue): Future[Int] = {
      db.update(selector.toSelector, update.toUpdate)
    }

    def remove(id: Id): Future[Boolean] = {
      db.remove(id.asJson.toSelector).map(_ > 0)
    }

    def remove(selector: JsValue): Future[Int] = {
      db.remove(selector.toSelector)
    }

    def find(id: Id, projection: Option[JsValue]): Future[Option[A]] = {
      db.find(
        id.asJson.toSelector,
        projection.flatMap(p => Some(p.toProjection)),
        None,
        0, 1
      ).map {
        case seq if seq.nonEmpty => Some(box(seq.head.toPublic))
        case seq => None
      }
    }

    def find(selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int): Future[Seq[A]] = {

      db.find(
        selector.toSelector,
        projection.flatMap(p => Some(p.toProjection)),
        sort.flatMap(s => Some(s.toSort)),
        page, perPage
      ).map {
        case seq if seq.nonEmpty => for (item <- seq) yield box(item.toPublic)
        case seq => Seq[A]()
      }
    }

    def findAndUpdate(entity: A): Future[Option[A]] = {
      entity.id match {
        case Some(id) => findAndUpdate(Json.obj("id" -> id), entity.asJson.as[JsObject] - "id" - "_version", None)
        case None => Future.failed(DaoErrors.MissingField("id", collectionName))
      }
    }

    def findAndUpdate(selector: JsValue, update: JsValue, sort: Option[JsValue]): Future[Option[A]] = {
      db.findAndUpdate(
        selector.toSelector,
        update.toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1)),
        sort.flatMap(s => Some(s.toSort))
      ).map { _.map { old =>
        box(old.toPublic)
      }}
    }

    def findAndRemove(id: Id): Future[Option[A]] = {
      db.findAndRemove(id.asJson.toSelector, None).map { _.map { removed =>
        box(removed.toPublic)
      }}
    }

    def findAndRemove(selector: JsValue, sort: Option[JsValue]): Future[Option[A]] = {
      db.findAndRemove(selector.toSelector, sort.flatMap(s => Some(s.toSort))).map { _.map { removed =>
        box(removed.toPublic)
      }}
    }

    /**
      * Provides raw access to ReactiveMongo, without any JSON transformation.
      */
    final def db = _db; private val _db = new {

      import play.modules.reactivemongo.json.BSONFormats._
      import services.common.mongo.commands._

      def count(selector: JsValue): Future[Int] = {
        val _selector = if (selector != null) selector else Json.obj()
        validateFields(_selector) match {
          case Failure(e) => Future.failed(e)
          case _ => ReactiveMongoPlugin.db.command(Count(
            collectionName,
            Some(_selector.toBson)
          )).recover {
            case e: LastError => throw DaoErrors.DatabaseError(collectionName, e)
          }
        }
      }

      def distinct(field: String, selector: JsValue): Future[Seq[String]] = {
        val _selector = if (selector != null) selector else Json.obj()
        validateFields(_selector) match {
          case Failure(e) => Future.failed(e)
          case _ =>
          ReactiveMongoPlugin.db.command(Distinct(
            collectionName,
            field,
            Some(_selector.toBson)
          )).recover {
            case e: LastError => throw DaoErrors.DatabaseError(collectionName, e)
          }
        }
      }

      def insert(entity: JsValue): Future[JsValue] = {
        if (entity == null || entity.as[JsObject].values.isEmpty) {
          Future.failed(DaoErrors.NoData(collectionName))
        } else validateFields(entity) match {
          case Failure(e) => Future.failed(e)
          case _ => collection.insert(entity).map(_ => entity).recover {
            case e: LastError => e.code match {
              case Some(11000) => throw DaoErrors.DuplicateKey(e.message.errorField.getOrElse("?"), collectionName)
              case _ => throw DaoErrors.DatabaseError(collectionName, e)
            }
          }
        }
      }

      def update(selector: JsValue, update: JsValue, upsert: Boolean = false, multi: Boolean = true): Future[Int] = {
        val _selector = if (selector != null) selector else Json.obj()
        if (update == null || update.as[JsObject].values.isEmpty) {
          Future.failed(DaoErrors.NoData(collectionName))
        } else validateFields(_selector, update) match {
          case Failure(e) => Future.failed(e)
          case _ => collection.update(
            selector = _selector,
            update = update,
            upsert = upsert,
            multi = multi
          ).map(_.n).recover {
            case e: LastError => e.code match {
              case Some(11000) => throw DaoErrors.DuplicateKey(e.message.errorField.getOrElse("?"), collectionName)
              case _ => throw DaoErrors.DatabaseError(collectionName, e)
            }
          }
        }
      }

      def remove(selector: JsValue): Future[Int] = {
        validateFields(if (selector != null) selector else Json.obj()) match {
          case Failure(e) => Future.failed(e)
          case _ => collection.remove(selector).map(_.n).recover {
            case e: LastError => throw DaoErrors.DatabaseError(collectionName, e)
          }
        }
      }

      def find(selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
        page: Int, perPage: Int): Future[Seq[JsValue]] = {

        val _selector = if (selector != null) selector else Json.obj()
        validateFields(_selector) match {
          case Failure(e) => Future.failed(e)
          case _ =>
            var query = collection.genericQueryBuilder.query(_selector).options(QueryOpts(skipN = page * perPage))
            projection.map(value => query = query.projection(value))
            sort.map(value => query = query.sort(value.as[JsObject]))
            query.cursor[JsValue].collect[Vector](perPage).recover {
              case e: LastError => throw DaoErrors.DatabaseError(collectionName, e)
            }
        }
      }

      def findAndUpdate(
        selector: JsValue, update: JsValue, sort: Option[JsValue],
        upsert: Boolean = false, fetchNew: Boolean = false
      ): Future[Option[JsValue]] = {
        val _selector = if (selector != null) selector else Json.obj()
        if (update == null || update.as[JsObject].values.isEmpty) {
          Future.failed(DaoErrors.NoData(collectionName))
        } else validateFields(_selector, update) match {
          case Failure(e) => Future.failed(e)
          case _ => ReactiveMongoPlugin.db.command(FindAndModify(
            collectionName,
            _selector.toBson,
            Update(update.toBson, fetchNew),
            upsert,
            sort.flatMap(s => Some(s.toBson))
          )).map { _.map { Json.toJson(_) }}.recover {
            case e: LastError => e.code match {
              case Some(11000) => throw DaoErrors.DuplicateKey(e.message.errorField.getOrElse("?"), collectionName)
              case _ => throw DaoErrors.DatabaseError(collectionName, e)
            }
          }
        }
      }

      def findAndRemove(selector: JsValue, sort: Option[JsValue]): Future[Option[JsValue]] = {
        val _selector = if (selector != null) selector else Json.obj()
        validateFields(_selector) match {
          case Failure(e) => Future.failed(e)
          case _ => ReactiveMongoPlugin.db.command(FindAndModify(
            collectionName,
            _selector.toBson,
            Remove,
            false,
            sort.flatMap(s => Some(s.toBson))
          )).map { _.map { Json.toJson(_) }}.recover {
            case e: LastError => throw DaoErrors.DatabaseError(collectionName, e)
          }
        }
      }
    }
  }
}
