/*#
  * @file MongoMessageDaoComponent.scala
  * @begin 19-Sep-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import reactivemongo.core.commands.LastError
import utils.common.TypeFactory
import services.common.DaoErrors._
import services.common.mongo.typeExtensions._
import services.common.mongo.VermongoDaoComponent
import services.messaging.MessageDaoComponent
import models.common.Id
import models.messaging.Message

/**
  * Implements the message DAO component for Mongo.
  */
trait MongoMessageDaoComponent extends VermongoDaoComponent[Message] with MessageDaoComponent {

  protected val typeFactory = new TypeFactory[Message] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("messages")

  fieldMaps = Map(
    "threadId" -> ("threadId", Some("$oid"))
  )

  collection.indexesManager.ensure(
    Index(List("threadId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("createdBy" -> IndexType.Ascending))
  )

  override def dao = new MongoMessageDao

  class MongoMessageDao extends VermongoDao with MessageDao {
  }
}
