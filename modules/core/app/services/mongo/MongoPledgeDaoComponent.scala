/*#
  * @file MongoPledgeDaoComponent.scala
  * @begin 20-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import scala.concurrent.Future
import org.joda.time.DateTime
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import reactivemongo.core.commands._
import utils.common.TypeFactory
import utils.common.Formats._
import services.common.DaoErrors._
import services.common.mongo.typeExtensions._
import services.common.mongo.VermongoDaoComponent
import services.core.PledgeDaoComponent
import models.core.Pledge

/**
  * Implements the pledge DAO component for Mongo.
  */
trait MongoPledgeDaoComponent extends VermongoDaoComponent[Pledge] with PledgeDaoComponent {

  protected val typeFactory = new TypeFactory[Pledge] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("pledges")

  fieldMaps = Map(
    "projectId" -> ("projectId", Some("$oid")),
    "rewardId" -> ("rewardId", Some("$oid")),
    "backerInfo.accountId" -> ("backerInfo.accountId", Some("$oid")),
    "state.timestamp" -> ("state.timestamp", Some("$date"))
  )

  collection.indexesManager.ensure(
    Index(List("projectId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("backerInfo.accountId" -> IndexType.Ascending))
  )

  override def dao = new MongoPledgeDao

  class MongoPledgeDao extends VermongoDao with PledgeDao {

    import services.core.pledges.PledgeFsm._

    def totals(state: Ztate)(implicit timeUs: DateTime): Future[Map[String, Double]] = totals(
      Json.obj("state.timestamp" -> Json.obj("$lt" -> Json.obj("$date" -> timeUs.getMillis)), "state.value" -> state)
    )

    def totals(selector: JsValue): Future[Map[String, Double]] = {
      ReactiveMongoPlugin.db.command(RawCommand(
        Json.obj("aggregate" -> collectionName, "pipeline" -> Json.arr(
          Json.obj("$match" -> selector.toSelector),
          Json.obj("$group" -> Json.obj("_id" -> "$amount.currency", "total" -> Json.obj("$sum" -> "$amount.value")))
        )).toBson
      )).map { result =>
        (result.toJson \ "result").as[scala.collection.immutable.List[JsValue]].map { js =>
          ((js \ "_id").as[String], (js \ "total").as[Double])
        } toMap
      }.recover {
        case e: LastError => throw DatabaseError(collectionName, e)
      }
    }
  }
}
