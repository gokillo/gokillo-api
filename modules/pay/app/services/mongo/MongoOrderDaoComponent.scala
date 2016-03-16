/*#
  * @file MongoOrderDaoComponent.scala
  * @begin 9-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.mongo.VermongoDaoComponent
import services.common.mongo.typeExtensions._
import services.pay.OrderDaoComponent
import models.common.Id
import models.pay.Order

/**
  * Implements the order DAO component for Mongo.
  */
trait MongoOrderDaoComponent extends VermongoDaoComponent[Order] with OrderDaoComponent {

  protected val typeFactory = new TypeFactory[Order] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("orders")

  fieldMaps = Map(
    "parentId" -> ("parentId", Some("$oid")),
    "accountId" -> ("accountId", Some("$oid")),
    "status.timestamp" -> ("status.timestamp", Some("$date"))
  )

  collection.indexesManager.ensure(
    Index(List("parentId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("peerId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("accountId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("refId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("coinAddress" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("orderType" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("status" -> IndexType.Ascending))
  )

  override def dao = new MongoOrderDao

  class MongoOrderDao extends VermongoDao with OrderDao {

    import utils.common.typeExtensions._

    def findRoot(id: Id): Future[Option[Order]] = {
      def findParent(order: Order): Future[Option[Order]] = order.parentId match {
        case Some(parentId) => find(parentId, None).flatMap {
          case Some(parent) => findParent(parent)
          case _ => Future.successful(Some(order)) // orphaned order returned as the root
        }
        case _ => Future.successful(Some(order))   // current order is already the root
      }

      find(id, None).flatMap {
        case Some(order) => findParent(order)
        case none => Future.successful(none)
      }
    }

    def findWithPrecision(
      selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int
    )(implicit precision: Precision): Future[Seq[Order]] = {
      val _selector = selector \ "amount.value" match {
        case _: JsUndefined => selector.toSelector
        case js: JsValue => selector.delete(__ \ Symbol("amount.value")).toSelector.as[JsObject] ++ Json.obj(
          "$where" -> s"""function() { var s = Math.pow(10, ${precision.length}); return Math.floor(this.amount.value * s) / s === ${js.as[Double] ~}; }"""
        )
      }

      db.find(
        _selector,
        projection.flatMap(p => Some(p.toProjection)),
        sort.flatMap(s => Some(s.toSort)),
        page, perPage
      ).map {
        case seq if seq.nonEmpty => seq.map(_.toPublic.as[Order])
        case seq => Seq[Order]()
      }
    }
  }
}
