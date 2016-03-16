/*#
  * @file MongoFeeDaoComponent.scala
  * @begin 30-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.mongo.MongoDaoComponent
import services.common.mongo.typeExtensions._
import services.core.FeeDaoComponent
import models.core.Fee

/**
  * Implements the fee DAO component for Mongo.
  */
trait MongoFeeDaoComponent extends MongoDaoComponent[Fee] with FeeDaoComponent {

  protected val typeFactory = new TypeFactory[Fee] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("fees")

  fieldMaps = Map(
    "projectId" -> ("projectId", Some("$oid")),
    "withdrawalTime" -> ("withdrawalTime", Some("$date"))
  )

  collection.indexesManager.ensure(
    Index(List("projectId" -> IndexType.Ascending))
  )

  override def dao = new MongoFeeDao

  class MongoFeeDao extends MongoDao with FeeDao {
  }
}
