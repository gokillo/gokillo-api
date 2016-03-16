/*#
  * @file MongoThreadDaoComponent.scala
  * @begin 15-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging.mongo

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import reactivemongo.core.commands.LastError
import utils.common.TypeFactory
import utils.common.Formats._
import services.common.DaoErrors._
import services.common.mongo.typeExtensions._
import services.common.mongo.VermongoDaoComponent
import services.messaging.ThreadDaoComponent
import models.common.Id
import models.messaging.Thread

/**
  * Implements the message thread DAO component for Mongo.
  */
trait MongoThreadDaoComponent extends VermongoDaoComponent[Thread] with ThreadDaoComponent {

  protected val typeFactory = new TypeFactory[Thread] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("threads")

  collection.indexesManager.ensure(
    Index(List("refId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("createdBy" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("subject" -> IndexType.Ascending))
  )

  override def dao = new MongoThreadDao

  class MongoThreadDao extends VermongoDao with ThreadDao {

    def incMessageCount(threadId: Id, by: Int): Future[Option[Int]] = {
      db.findAndUpdate(
        threadId.asJson.fromPublic,
        Json.obj("lastActivityTime" -> DateTime.now(DateTimeZone.UTC)).toUpdate ++
        Json.obj("$inc" -> Json.obj("messageCount" -> by, "_version" -> 1)),
        None
      ).map {
        // do not version old value
        case Some(old) => old.toPublic.as[Thread].messageCount
        case _ => None
      }
    }

    def addGrantees(threadId: Id, grantees: List[String]): Future[Unit] = {
      db.find(
        threadId.asJson.fromPublic,
        Some(Json.obj("grantees" -> 1)),
        None,
        0, 1
      ).map { seq => if (seq.nonEmpty) {
        val currentGrantees = seq.head.get(__ \ 'grantees) match {
          case _: JsUndefined => Seq[JsValue]()
          case js: JsValue => js.as[JsArray].value
        }

        val granteesToAdd = grantees.filterNot(grantee => currentGrantees.contains(JsString(grantee)))

        if (granteesToAdd.length > 0) {
          var selector = Json.obj("_id" -> seq.head \ "_id")
          if (currentGrantees.nonEmpty) selector = selector ++ Json.obj("grantees" -> currentGrantees)

          val update = Json.obj(
            "grantees" -> (currentGrantees ++ Json.toJson(granteesToAdd).as[JsArray].value)
          ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false)
            case _ => throw StaleObject(threadId.value.get, collectionName)
          }
        }
      }}
    }

    def removeGrantees(threadId: Id, grantees: List[String]): Future[Unit] = {
      db.find(
        threadId.asJson.fromPublic,
        Some(Json.obj("grantees" -> 1)),
        None,
        0, 1
      ).map { seq => if (seq.nonEmpty) {
        val currentGrantees = seq.head.get(__ \ 'grantees) match {
          case _: JsUndefined => Seq[JsValue]()
          case js: JsValue => js.as[JsArray].value
        }

        val granteesToKeep = currentGrantees.filterNot(grantee => grantees.contains(grantee.as[String]))

        if (granteesToKeep.length != currentGrantees.length) {
          val selector = Json.obj(
            "_id" -> seq.head \ "_id",
            "grantees" -> currentGrantees
          )

          val update = { if (granteesToKeep.length > 0) {
            Json.obj("grantees" -> granteesToKeep).toUpdate
          } else {
            Json.obj("$unset" -> Json.obj("grantees" -> JsNull))
          }} ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false)
            case _ => throw StaleObject(threadId.value.get, collectionName)
          }
        }
      }}
    }
  }
}
