/*#
  * @file MongoUserDaoComponent.scala
  * @begin 18-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.mongo

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import utils.common.TypeFactory
import services.common.CommonErrors._
import services.common.DaoErrors._
import services.common.mongo.typeExtensions._
import services.common.mongo.VermongoDaoComponent
import services.auth.UserDaoComponent
import models.common.{Address, Id}
import models.auth.{User, MetaAccount}

/**
  * Implements the user DAO component for Mongo.
  */
trait MongoUserDaoComponent extends VermongoDaoComponent[User] with UserDaoComponent {

  protected val typeFactory = new TypeFactory[User] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("users")

  fieldMaps = Map(
    "birthDate" -> ("birthDate", Some("$localdate")),
    "state.timestamp" -> ("state.timestamp", Some("$date")),
    "metaAccounts.id" -> ("metaAccounts._id", Some("$oid")),
    "activationTime" -> ("activationTime", Some("$date")),
    "metaAccounts.activationTime" -> ("metaAccounts.activationTime", Some("$date"))
  )

  collection.indexesManager.ensure(
    Index(List("email" -> IndexType.Ascending), unique = true)
  )

  collection.indexesManager.ensure(
    Index(List("username" -> IndexType.Ascending), unique = true)
  )

  override def dao = new MongoUserDao

  class MongoUserDao extends VermongoDao with UserDao {
 
    def findByAccountId(accountId: Id, ifPublic: Boolean): Future[Option[User]] = {
      var exclude = Json.obj("password" -> 0, "addresses" -> 0, "metaAccounts" -> 0, "public" -> 0, "_version" -> 0)
      var public = ifPublic match {
        case true => exclude = exclude ++ Json.obj("email" -> 0); Some(true)
        case _ => None
      }

      val selectorWrites = (
        (__ \ "metaAccounts._id" \ "$oid").writeNullable[String] ~
        (__ \ "public").writeNullable[Boolean]
      ).tupled

      db.find(
        Json.toJson(accountId.value, public)(selectorWrites),
        Some(exclude), None, 0, 1
      ).map {
        case seq if seq.nonEmpty => Some(seq.head.toPublic.as[User])
        case _ => None
      }
    }

    def addAddress(userId: Id, address: Address): Future[Option[Int]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val addresses = seq.head.get(__ \ 'addresses) match {
            case _: JsUndefined => address.default = true; Seq[JsValue]()
            case js: JsValue => js.as[JsArray].value
          }

          addresses.map(_.get(__ \ 'name).as[String]).indexOf(address.name.get) match {
            case index if index < 0 =>
              var selector = Json.obj("_id" -> seq.head \ "_id")
              if (addresses.length > 0) selector = selector ++ Json.obj("addresses" -> addresses)

              val update = Json.obj(
                "addresses" -> ((if (address.default) addresses.map { address => address.delete(__ \ 'default) match {
                  case _: JsUndefined => address
                  case js: JsValue => js
                }} else addresses) :+ address.asJson)
              ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

              db.findAndUpdate(selector, update, None).map {
                case Some(old) => version(old, false); Some(addresses.length)
                case _ => throw StaleObject(userId.value.get, collectionName)
              }
            case _ => Future.failed(DuplicateKey("address.name", collectionName))
          }
        case _ => Future.successful(None)
      }
    }

    def updateAddress(userId: Id, index: Int, address: Address): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => updateAddress(seq.head, index, address)
        case _ => Future.successful(None)
      }
    }

    def updateAddressByName(userId: Id, name: String, address: Address): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'addresses) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { address =>
              address.get(__ \ 'name).as[String]
            } indexOf(name)
          }
          updateAddress(seq.head, index, address)
        case _ => Future.successful(None)
      }
    }

    def updateDefaultAddress(userId: Id, address: Address): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'addresses) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { address =>
              address.get(__ \ 'default) match {
                case _: JsUndefined => false
                case default: JsValue => default.as[Boolean]
            }} indexOf(true)
          }
          updateAddress(seq.head, index, address)
        case _ => Future.successful(None)
      }
    }

    private def updateAddress(user: JsValue, index: Int, address: Address): Future[Option[Address]] = {
      val addresses = user.get(__ \ 'addresses) match {
        case _: JsUndefined => Seq[JsValue]()
        case js: JsValue => js.as[JsArray].value
      }

      if (index > -1 && index < addresses.length) {
        val nameAt = address.name.map { name =>
          addresses.map(_.get(__ \ 'name).as[String]).indexOf(name)
        } getOrElse -1

        if (nameAt < 0 || nameAt == index) {
          val selector = Json.obj(
            "_id" -> user \ "_id",
            "addresses" -> addresses
          )

          val update = Json.obj(
            "addresses" -> ((if (address.default) addresses.map { address => address.delete(__ \ 'default) match {
              case _: JsUndefined => address
              case js: JsValue => js
            }} else addresses).patch(
              index, Seq(addresses(index).as[JsObject] ++ address.asJson.as[JsObject]), 1
            ))
          ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false); Some(addresses(index).as[Address])
            case _ => throw StaleObject(user.get(__ \ '_id).as[String], collectionName)
          }
        } else Future.failed(DuplicateKey("address.name", collectionName))
      } else Future.successful(None)
    }

    def setDefaultAddress(userId: Id, index: Int) = updateAddress(userId, index, Address(default = Some(true)))

    def setDefaultAddressByName(userId: Id, name: String) = updateAddressByName(userId, name, Address(default = Some(true)))

    def removeAddress(userId: Id, index: Int): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => removeAddress(seq.head, index)
        case _ => Future.successful(None)
      }
    }

    def removeAddressByName(userId: Id, name: String): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'addresses) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { address =>
              address.get(__ \ 'name).as[String]
            } indexOf(name)
          }
          removeAddress(seq.head, index)
        case _ => Future.successful(None)
      }
    }

    private def removeAddress(user: JsValue, index: Int): Future[Option[Address]] = {
      val addresses = user.get(__ \ 'addresses) match {
        case _: JsUndefined => Seq[JsValue]()
        case js: JsValue => js.as[JsArray].value
      }

      if (index > -1 && index < addresses.length) {
      if (addresses(index).get(__ \ 'default) match {
        case b: JsBoolean => b.value
        case _ => false
      }) {
        Future.failed(ElementNotRemoveable(
          "address", addresses(index).get(__ \ 'name).as[String],
          "user", user.get(__ \ '_id).as[String]
        ))
      } else {
        val selector = Json.obj(
          "_id" -> user \ "_id",
          s"addresses.$index" -> addresses(index)
        )

        val update = { if (addresses.length > 1) {
          user.delete(__ \ '_id).delete((__ \ 'addresses)(index)).toUpdate
        } else {
          Json.obj("$unset" -> Json.obj("addresses" -> JsNull))
        }} ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

        db.findAndUpdate(selector, update, None).map {
          case Some(old) => version(old, false); Some(addresses(index).as[Address])
          case _ => throw StaleObject(user.get(__ \ '_id).as[String], collectionName)
        }
      }} else Future.successful(None)
    }

    def findAddress(userId: Id, index: Int): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "addresses" -> 1,
          "addresses" -> Json.obj("$slice" -> Json.arr(index, 1))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { user => (user \ "addresses") match {
        case _: JsUndefined => None
        case js: JsValue => js.as[Seq[Address]].headOption
      }}}
    }

    def findAddressByName(userId: Id, name: String): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "addresses" -> 1,
          "addresses" -> Json.obj("$elemMatch" -> Json.obj("name" -> name))
        )),
        None,
        0, 1
      ).map  { _.headOption.flatMap { user => (user \ "addresses") match {
        case _: JsUndefined => None
        case js: JsValue => js.as[Seq[Address]].headOption
      }}}
    }

    def findDefaultAddress(userId: Id): Future[Option[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "addresses" -> 1,
          "addresses" -> Json.obj("$elemMatch" -> Json.obj("default" -> true))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { user => (user \ "addresses") match {
        case _: JsUndefined => None
        case js: JsValue => js.as[Seq[Address]].headOption
      }}}
    }

    def findAddresses(userId: Id): Future[Seq[Address]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("addresses" -> 1)),
        None,
        0, 1
      ).map { _.headOption.map { user => (user \ "addresses") match {
        case _: JsUndefined => Seq()
        case js: JsValue => js.as[Seq[Address]]
      }} getOrElse Seq() }
    }

    def activateAccount(userId: Id, accountId: Id): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'metaAccounts) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ '_id \ '$oid).as[String]
            } indexOf(accountId.value.get)
          }
          updateAccount(seq.head, index, MetaAccount(activationTime = Some(DateTime.now(DateTimeZone.UTC))))
        case _ => Future.successful(None)
      }
    }

    def renameAccount(userId: Id, index: Int, newName: String): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => updateAccount(seq.head, index, MetaAccount(name = Some(newName)))
        case _ => Future.successful(None)
      }
    }

    def renameAccountById(userId: Id, accountId: Id, newName: String): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'metaAccounts) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ '_id \ '$oid).as[String]
            } indexOf(accountId.value.get)
          }
          updateAccount(seq.head, index, MetaAccount(name = Some(newName)))
        case _ => Future.successful(None)
      }
    }

    def renameAccountByName(userId: Id, name: String, newName: String): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'metaAccounts) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ 'name).as[String]
            } indexOf(name)
          }
          updateAccount(seq.head, index, MetaAccount(name = Some(newName)))
        case _ => Future.successful(None)
      }
    }

    def renameDefaultAccount(userId: Id, newName: String): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ 'metaAccounts) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ 'default) match {
                case _: JsUndefined => false
                case default: JsValue => default.as[Boolean]
            }} indexOf(true)
          }
          updateAccount(seq.head, index, MetaAccount(name = Some(newName)))
        case _ => Future.successful(None)
      }
    }

    def setDefaultAccount(userId: Id, index: Int) = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => updateAccount(seq.head, index, MetaAccount(default = Some(true)))
        case _ => Future.successful(None)
      }
    }

    def setDefaultAccountById(userId: Id, accountId: Id) = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ "metaAccounts") match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ '_id \ '$oid).as[String]
            } indexOf(accountId.value.get)
          }
          updateAccount(seq.head, index, MetaAccount(default = Some(true)))
        case _ => Future.successful(None)
      }
    }

    def setDefaultAccountByName(userId: Id, name: String) = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val index = seq.head.get(__ \ "metaAccounts") match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { metaAccount =>
              metaAccount.get(__ \ 'name).as[String]
            } indexOf(name)
          }
          updateAccount(seq.head, index, MetaAccount(default = Some(true)))
        case _ => Future.successful(None)
      }
    }

    private def updateAccount(user: JsValue, index: Int, metaAccount: MetaAccount): Future[Option[MetaAccount]] = {
      val metaAccounts = user.get(__ \ 'metaAccounts) match {
        case _: JsUndefined => Seq[JsValue]()
        case js: JsValue => js.as[JsArray].value
      }

      if (index > -1 && index < metaAccounts.length) {
        val nameAt = metaAccount.name.map { name =>
          metaAccounts.map(_.get(__ \ 'name).as[String]).indexOf(name)
        } getOrElse -1

        if (nameAt < 0 || nameAt == index) {
          val selector = Json.obj(
            "_id" -> user \ "_id",
            "metaAccounts" -> metaAccounts
          )

          val update = Json.obj(
            "metaAccounts" -> ((if (metaAccount.default) metaAccounts.map { metaAccount => metaAccount.delete(__ \ 'default) match {
              case _: JsUndefined => metaAccount
              case js: JsValue => js
            }} else metaAccounts).patch(
              index, Seq(metaAccounts(index).as[JsObject] ++ metaAccount.asJson.as[JsObject]), 1
            ))
          ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false); Some(metaAccounts(index).toPublic.as[MetaAccount])
            case _ => throw StaleObject(user.get(__ \ '_id).as[String], collectionName)
          }
        } else Future.failed(DuplicateKey("metaAccount.name", collectionName))
      } else Future.successful(None)
    }

    def findAccount(userId: Id, index: Int): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "metaAccounts" -> 1,
          "metaAccounts" -> Json.obj("$slice" -> Json.arr(index, 1))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "metaAccounts" match {
        case _: JsUndefined => None
        case js: JsValue => js.toPublic.as[Seq[MetaAccount]].headOption
      }}}
    }

    def findAccountById(userId: Id, accountId: Id): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "metaAccounts" -> 1,
          "metaAccounts" -> Json.obj("$elemMatch" -> Json.obj("_id" -> Json.obj("$oid" -> accountId.value)))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "metaAccounts" match {
        case _: JsUndefined => None
        case js: JsValue => js.toPublic.as[Seq[MetaAccount]].headOption
      }}}
    }

    def findAccountByName(userId: Id, name: String): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "metaAccounts" -> 1,
          "metaAccounts" -> Json.obj("$elemMatch" -> Json.obj("name" -> name))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "metaAccounts" match {
        case _: JsUndefined => None
        case js: JsValue => js.toPublic.as[Seq[MetaAccount]].headOption
      }}}
    }

    def findDefaultAccount(userId: Id): Future[Option[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj(
          "metaAccounts" -> 1,
          "metaAccounts" -> Json.obj("$elemMatch" -> Json.obj("default" -> true))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "metaAccounts" match {
        case _: JsUndefined => None
        case js: JsValue => js.toPublic.as[Seq[MetaAccount]].headOption
      }}}
    }

    def findAccounts(userId: Id): Future[Seq[MetaAccount]] = {
      db.find(
        userId.asJson.fromPublic,
        Some(Json.obj("metaAccounts" -> 1)),
        None,
        0, 1
      ).map { _.headOption.map { _ \ "metaAccounts" match {
        case _: JsUndefined => Seq()
        case js: JsValue => js.toPublic.as[Seq[MetaAccount]]
      }} getOrElse Seq() }
    }
  }
}
