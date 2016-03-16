/*#
  * @file MongoTokenTraceDaoComponent.scala
  * @begin 26-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.mongo

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import utils.common.TypeFactory
import utils.common.Formats._
import services.common.mongo.MongoDaoComponent
import services.common.mongo.typeExtensions._
import services.auth.TokenTraceDaoComponent
import models.common.Id
import models.auth.{Token, TokenTrace}

/**
  * Implements the token trace DAO component for Mongo.
  */
trait MongoTokenTraceDaoComponent extends MongoDaoComponent[TokenTrace] with TokenTraceDaoComponent {

  protected val typeFactory = new TypeFactory[TokenTrace] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("tokenTraces")

  fieldMaps = Map(
    "expirationTime" -> ("expirationTime", Some("$date"))
  )

  override def dao = new MongoTokenTraceDao

  class MongoTokenTraceDao extends MongoDao with TokenTraceDao {

    def findAndUpdate(token: Token): Future[Option[TokenTrace]] = {
      db.findAndUpdate(
        Json.obj(
          "_id" -> Json.obj("$oid" -> token.id),
          "expirationTime" -> Json.obj("$gt" -> Json.obj("$date" -> DateTime.now(DateTimeZone.UTC).getMillis))
        ),
        Json.obj(
          "expirationTime" -> Json.obj("$date" -> token.expirationTime.getMillis)
        ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1)),
        None
      ).map(_.map(box(_)))
    }

    def removeExpired: Future[Int] = db.remove(Json.obj(
      "expirationTime" -> Json.obj("$lte" -> Json.obj("$date" -> DateTime.now(DateTimeZone.UTC).getMillis))
    ))
  }
}
