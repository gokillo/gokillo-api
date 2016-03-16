/*#
  * @file VermongoDaoComponent.scala
  * @begin 1-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.core.commands.LastError
import models.common.{Id, JsEntity}
import utils.common.WithRetry

/**
  * Defines functionality for Mongo data access objects with versioning support.
  * @tparam A The type this `VermongoDaoComponent` provides accesss for.
  */
trait VermongoDaoComponent[A <: JsEntity] extends MongoDaoComponent[A] {

  /**
    * Gets an instance of the [[Dao]] implementation for Mongo with
    * versioning support.
    *
    * @return An instance of the [[Dao]] implementation for Mongo with
    *         versioning support.
    */
  override def dao = new VermongoDao

  /**
    * Implements data access object for Mongo with versioning support.
    */
  class VermongoDao extends MongoDao {

    import typeExtensions._

    private val shadowCollection = ReactiveMongoPlugin.db.collection[JSONCollection](
      collection.name + ".vermongo"
    )

    override def update(entity: A): Future[Boolean] = {
      findAndUpdate(entity).map(_.isDefined)
    }

    override def update(selector: JsValue, update: JsValue): Future[Int] = {
      var count = 0

      WithRetry {
        // continue until findAndUpdate does not return 0
        (n: Int) => n == 0
      } {
        findAndUpdate(selector, update, None).map {
          case Some(_) => count = count + 1; 1
          case _ => 0
        }
      } map { _ => count }
    }

    override def remove(id: Id): Future[Boolean] = {
      findAndRemove(id.asJson, None).map(_.isDefined)
    }

    override def remove(selector: JsValue): Future[Int] = {
      findAndRemove(selector, None).map {
        case Some(_) => 1
        case _ => 0
      }
    }

    override def findAndUpdate(selector: JsValue, update: JsValue, sort: Option[JsValue]): Future[Option[A]] = {
      db.findAndUpdate(
        selector.toSelector,
        update.toUpdate.as[JsObject] ++ Json.obj("$inc" -> Json.obj("_version" -> 1)),
        sort.flatMap(s => Some(s.toSort))
      ).map { _.map { old =>
        version(old, false)
        box(old.toPublic)
      }}
    }

    override def findAndRemove(id: Id): Future[Option[A]] = {
      findAndRemove(id.asJson, None)
    }

    override def findAndRemove(selector: JsValue, sort: Option[JsValue]): Future[Option[A]] = {
      db.findAndRemove(selector.toSelector, sort.flatMap(s => Some(s.toSort))).map { _.map { removed =>
        version(removed, true)
        box(removed.toPublic)
      }}
    }

    protected def version(obj: JsValue, removed: Boolean): Future[LastError] = {
      shadowCollection.insert(obj.toVersioned(removed).as[JsObject]).map { lastError =>
        if (!lastError.ok) Logger.error(s"error versioning obj ${obj.get(__ \ 'id \ '$oid)}", lastError)
        lastError
      }
    }
  }
}
