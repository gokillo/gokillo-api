/*#
  * @file MongoNotificationDaoComponent.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import utils.common.TypeFactory
import services.common.mongo.VermongoDaoComponent
import services.common.mongo.typeExtensions._
import services.messaging.NotificationDaoComponent
import models.messaging.Notification

/**
  * Implements the notification DAO component for Mongo.
  */
trait MongoNotificationDaoComponent extends VermongoDaoComponent[Notification] with NotificationDaoComponent {

  protected val typeFactory = new TypeFactory[Notification] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("notifications")

  fieldMaps = Map(
    "sentTime" -> ("sentTime", Some("$date"))
  )

  override def dao = new MongoNotificationDao

  class MongoNotificationDao extends VermongoDao with NotificationDao {
  }
}
