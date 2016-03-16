/*#
  * @file MongoApiConsumerDaoComponent.scala
  * @begin 31-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.mongo.VermongoDaoComponent
import services.common.mongo.typeExtensions._
import services.auth.ApiConsumerDaoComponent
import models.common.Id
import models.auth.ApiConsumer

/**
  * Implements the API consumer DAO component for Mongo.
  */
trait MongoApiConsumerDaoComponent extends VermongoDaoComponent[ApiConsumer] with ApiConsumerDaoComponent {

  protected val typeFactory = new TypeFactory[ApiConsumer] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("apiConsumers")

  fieldMaps = Map(
    "accountId" -> ("accountId", Some("$oid")),
    "ownerId" -> ("ownerId", Some("$oid"))
  )

  collection.indexesManager.ensure(
    Index(List("accountId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("ownerId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("name" -> IndexType.Ascending), unique = true)
  )

  override def dao = new MongoApiConsumerDao

  class MongoApiConsumerDao extends VermongoDao with ApiConsumerDao {

    def updateApiKey(apiConsumerId: Id, apiKey: String): Future[Option[String]] = {
      // don't version the MongoDB document since api keys are renewed
      // automatically many times a day

      db.findAndUpdate(
        apiConsumerId.asJson.fromPublic, Json.obj("apiKey" -> apiKey).toUpdate, None
      ).map(_.map(_ as (__ \ 'apiKey).read[String]))
    }
  }
}
