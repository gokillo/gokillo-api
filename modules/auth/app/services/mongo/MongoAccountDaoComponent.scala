/*#
  * @file MongoAccountDaoComponent.scala
  * @begin 2-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.DaoErrors._
import services.common.mongo.VermongoDaoComponent
import services.common.mongo.typeExtensions._
import services.auth.AccountDaoComponent
import models.common.Id
import models.auth.{Account, Role}
import models.auth.Role._

/**
  * Implements the account DAO component for Mongo.
  */
trait MongoAccountDaoComponent extends VermongoDaoComponent[Account] with AccountDaoComponent {

  protected val typeFactory = new TypeFactory[Account] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("accounts")

  fieldMaps = Map(
    "ownerId" -> ("ownerId", Some("$oid"))
  )

  collection.indexesManager.ensure(
    Index(List("ownerId" -> IndexType.Ascending))
  )

  override def dao = new MongoAccountDao

  class MongoAccountDao extends VermongoDao with AccountDao {

    def addRoles(accountId: Id, roles: List[Role]): Future[Unit] = {
      db.find(
        accountId.asJson.fromPublic,
        Some(Json.obj("roles" -> 1)),
        None,
        0, 1
      ).map { seq => if (seq.nonEmpty) {
        val currentRoles = seq.head.get(__ \ 'roles) match {
          case _: JsUndefined => Seq[JsValue]()
          case js: JsValue => js.as[JsArray].value
        }

        val rolesToAdd = roles.filterNot(role => currentRoles.contains(JsNumber(role.id))).map(_.id)

        if (rolesToAdd.length > 0) {
          val selector = Json.obj(
            "_id" -> seq.head \ "_id",
            "roles" -> currentRoles
          )

          val update = Json.obj(
            "roles" -> (currentRoles ++ Json.toJson(rolesToAdd).as[JsArray].value)
          ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false)
            case _ => throw StaleObject(accountId.value.get, collectionName)
          }
        }
      }}
    }

    def removeRoles(accountId: Id, roles: List[Role]): Future[Unit] = {
      db.find(
        accountId.asJson.fromPublic,
        Some(Json.obj("roles" -> 1)),
        None,
        0, 1
      ).map { seq => if (seq.nonEmpty) {
        val currentRoles = seq.head.get(__ \ 'roles) match {
          case _: JsUndefined => Seq[JsValue]()
          case js: JsValue => js.as[JsArray].value
        }

        val rolesToKeep = currentRoles.filterNot(role => roles.contains(Role(role.as[Int])))

        if (rolesToKeep.length != currentRoles.length) {
          val selector = Json.obj(
            "_id" -> seq.head \ "_id",
            "roles" -> currentRoles
          )

          val update = { if (rolesToKeep.length > 0) {
            Json.obj("roles" -> rolesToKeep).toUpdate
          } else {
            Json.obj("$unset" -> Json.obj("roles" -> JsNull))
          }} ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false)
            case _ => throw StaleObject(accountId.value.get, collectionName)
          }
        }
      }}
    }
  }
}
