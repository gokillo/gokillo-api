/*#
  * @file MongoLeftoverDaoComponent.scala
  * @begin 6-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.mongo.MongoDaoComponent
import services.common.mongo.typeExtensions._
import services.core.LeftoverDaoComponent
import models.core.Leftover
import models.pay.Coin

/**
  * Implements the leftover DAO component for Mongo.
  */
trait MongoLeftoverDaoComponent extends MongoDaoComponent[Leftover] with LeftoverDaoComponent {

  protected val typeFactory = new TypeFactory[Leftover] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("leftovers")

  fieldMaps = Map(
    "withdrawalTime" -> ("withdrawalTime", Some("$date"))
  )

  override def dao = new MongoLeftoverDao

  class MongoLeftoverDao extends MongoDao with LeftoverDao {

    def incAmount(by: Coin): Future[Coin] = {
      db.findAndUpdate(
        Json.obj("withdrawalTime" -> Json.obj("$exists" -> false)),
        Json.obj(
          "$setOnInsert" -> Json.obj(
            "amount.currency" -> by.currency,
            "creationTime" -> Json.obj("$date" -> DateTime.now(DateTimeZone.UTC).getMillis)
          ),
          "$inc" -> Json.obj(
            "amount.value" -> by.value,
            "count" -> 1
          )
        ),
        None,
        true // create leftover if it does not exist
      ).map {
        case Some(old) => old.toPublic.as[Leftover].amount
        case _ => Coin(0.0, by.currency)
      }
    }

    def reset: Future[Option[Leftover]] = {
      db.findAndUpdate(
        Json.obj("withdrawalTime" -> Json.obj("$exists" -> false)),
        Json.obj("$set" -> Json.obj(
          "withdrawalTime" -> Json.obj("$date" -> DateTime.now(DateTimeZone.UTC).getMillis))
        ),
        None
      ).map {
        case Some(old) => Some(old.toPublic.as[Leftover])
        case _ => None
      }
    }

    def findCurrent: Future[Option[Leftover]] = {
      db.find(
        Json.obj("withdrawalTime" -> Json.obj("$exists" -> false)),
        None, None, 0, 1
      ).map {
        case seq if seq.nonEmpty => Some(seq.head.toPublic.as[Leftover])
        case seq => None
      }
    }

    def findWithdrawn(page: Int, perPage: Int): Future[Seq[Leftover]] = {
      db.find(
        Json.obj("withdrawalTime" -> Json.obj("$exists" -> true)),
        None,
        Some(Json.obj("$asc" -> Json.arr("withdrawalTime"))),
        page, perPage
      ).map {
        case seq if seq.nonEmpty => seq.map(_.toPublic.as[Leftover])
        case seq => Seq[Leftover]()
      }
    }
  }
}
