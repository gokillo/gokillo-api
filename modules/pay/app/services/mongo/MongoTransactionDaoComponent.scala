/*#
  * @file MongoTransactionDaoComponent.scala
  * @begin 8-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay.mongo

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
import services.pay.TransactionDaoComponent
import models.pay.Transaction

/**
  * Implements the transaction DAO component for Mongo.
  */
trait MongoTransactionDaoComponent extends VermongoDaoComponent[Transaction] with TransactionDaoComponent {

  protected val typeFactory = new TypeFactory[Transaction] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("transactions")

  fieldMaps = Map(
    "orderId" -> ("orderId", Some("$oid"))
  )

  collection.indexesManager.ensure(
    Index(List("orderId" -> IndexType.Ascending))
  )

  override def dao = new MongoTransactionDao

  class MongoTransactionDao extends VermongoDao with TransactionDao {
  }
}
