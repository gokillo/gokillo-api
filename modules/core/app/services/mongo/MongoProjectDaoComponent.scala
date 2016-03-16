/*#
  * @file MongoProjectDaoComponent.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.ReactiveMongoPlugin
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.indexes._
import reactivemongo.core.commands._
import utils.common.TypeFactory
import utils.common.typeExtensions._
import services.common.DaoErrors._
import services.common.mongo.typeExtensions._
import services.common.mongo.VermongoDaoComponent
import services.core.ProjectDaoComponent
import models.common.Id
import models.core.{Project, FundingInfo, Reward, Faq}

/**
  * Implements the project DAO component for Mongo.
  */
trait MongoProjectDaoComponent extends VermongoDaoComponent[Project] with ProjectDaoComponent {

  protected val typeFactory = new TypeFactory[Project] {}
  protected val collection = ReactiveMongoPlugin.db.collection[JSONCollection]("projects" + (if (wip) ".wip" else ""))

  fieldMaps = Map(
    "accountId" -> ("accountId", Some("$oid")),
    "state.timestamp" -> ("state.timestamp", Some("$date")),
    "fundingInfo.startTime" -> ("fundingInfo.startTime", Some("$date")),
    "fundingInfo.endTime" -> ("fundingInfo.endTime", Some("$date")),
    "rewards.id" -> ("rewards._id", Some("$oid")),
    "estimatedDeliveryDate" -> ("estimatedDeliveryDate", Some("$localdate")),
    "rewards.estimatedDeliveryDate" -> ("rewards.estimatedDeliveryDate", Some("$localdate")),
    "history.time" -> ("history.time", Some("$date"))
  )

  collection.indexesManager.ensure(
    Index(List("accountId" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("name" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("categories" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("location" -> IndexType.Ascending))
  )

  collection.indexesManager.ensure(
    Index(List("fundingInfo.coinAddress" -> IndexType.Ascending), unique = true)
  )

  def wip: Boolean

  override def dao = new MongoProjectDao

  class MongoProjectDao extends VermongoDao with ProjectDao {

    import scala.collection.immutable.{List => ImmutableList}
    import services.core.projects.ProjectFsm._
    import services.pay.PayGateway

    def incRaisedAmount(
      projectId: Id, by: Double
    )(implicit state: Ztate): Future[Option[FundingInfo]] = {
      db.findAndUpdate(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Json.obj("$inc" -> Json.obj(
          "fundingInfo.raisedAmount" -> by,
          "fundingInfo.pledgeCount" -> 1,
          "_version" -> 1
        )),
        None
      ).map {
        case Some(old) => version(old, false); old.toPublic.as[Project].fundingInfo
        case _ => None
      }
    }

    override def insert(project: Project): Future[Project] = {
      project.rewards match {
        case Some(rewards) if rewards.map(_.pledgeAmount.get).containsDuplicates => Future.failed(DuplicateKey("reward.pledgeAmount", collectionName))
        case _ => super.insert(project)
      }
    }

    override def update(project: Project): Future[Boolean] = {
      project.rewards match {
        case Some(rewards) if rewards.map(_.pledgeAmount.get).containsDuplicates => Future.failed(DuplicateKey("reward.pledgeAmount", collectionName))
        case _ => super.update(project)
      }
    }

    override def update(selector: JsValue, update: JsValue): Future[Int] = {
      update.get(__ \ 'rewards) match {
        case _: JsUndefined => db.update(selector.toSelector, update.toUpdate)
        case js: JsValue => js.as[JsArray].as[ImmutableList[Reward]] match {
          case rewards if rewards.map(_.pledgeAmount.get).containsDuplicates => Future.failed(DuplicateKey("reward.pledgeAmount", collectionName))
          case _ => db.update(selector.toSelector, update.toUpdate)
        }
      }
    }

    def addReward(
      projectId: Id, reward: Reward
    )(implicit state: Ztate): Future[Option[Int]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("fundingInfo" -> 1, "rewards" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          implicit val precision = seq.head.get(__ \ 'fundingInfo \ 'currency) match {
            case _: JsUndefined => Precision(0.00000001) // should never happen
            case js: JsValue => Precision(if (js.as[JsString].value == PayGateway.Cryptocurrency) 0.00000001 else 0.01) 
          }

          val rewards = seq.head.get(__ \ 'rewards) match {
            case _: JsUndefined => Seq[JsValue]()
            case js: JsValue => js.as[JsArray].value
          }

          // round half-up pledge amount
          reward.pledgeAmount = reward.pledgeAmount.map(_ ~~)

          rewards.map(_.get(__ \ 'pledgeAmount).as[Double]).indexOf(reward.pledgeAmount.get) match {
            case index if index < 0 =>
              var selector = Json.obj("_id" -> seq.head \ "_id")
              if (rewards.length > 0) selector = selector ++ Json.obj("rewards" -> rewards)

              val update = Json.obj(
                "rewards" -> (rewards :+ reward.asJson.set(__ \ '_id \ '$oid -> JsString(generateId)))
              ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

              db.findAndUpdate(selector, update, None).map {
                case Some(old) => version(old, false); Some(rewards.length)
                case _ => throw StaleObject(projectId.value.get, collectionName)
              }
            case _ => Future.failed(DuplicateKey("reward.pledgeAmount", collectionName))
          }
        case _ => Future.successful(None)
      }
    }

    def updateReward(
      projectId: Id, index: Int, reward: Reward
    )(implicit state: Ztate): Future[Option[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("rewards" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => updateReward(seq.head, index, reward)
        case _ => Future.successful(None)
      }
    }

    def updateRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double, reward: Reward
    )(implicit state: Ztate): Future[Option[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("fundingInfo" -> 1, "rewards" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          implicit val precision = seq.head.get(__ \ 'fundingInfo \ 'currency) match {
            case _: JsUndefined => Precision(0.00000001) // should never happen
            case js: JsValue => Precision(if (js.as[JsString].value == PayGateway.Cryptocurrency) 0.00000001 else 0.01) 
          }

          // round half-up pledge amount
          reward.pledgeAmount = reward.pledgeAmount.map(_ ~~)

          val index = seq.head.get(__ \ 'rewards) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { reward =>
              reward.get(__ \ 'pledgeAmount).as[Double]
            } indexOf(pledgeAmount ~~)
          }
          updateReward(seq.head, index, reward)
        case _=> Future.successful(None)
      }
    }

    private def updateReward(project: JsValue, index: Int, reward: Reward): Future[Option[Reward]] = {
      val rewards = project.get(__ \ 'rewards) match {
        case _: JsUndefined => Seq[JsValue]()
        case js: JsValue => js.as[JsArray].value
      }

      if (index > -1 && index < rewards.length) {
        val pledgeAmountAt = reward.pledgeAmount.map { pledgeAmount =>
          rewards.map(_.get(__ \ 'pledgeAmount).as[Double]).indexOf(pledgeAmount)
        } getOrElse -1

        if (pledgeAmountAt < 0 || pledgeAmountAt == index) {
          val selector = Json.obj(
            "_id" -> project \ "_id",
            "rewards" -> rewards
          )

          val updatedReward = reward.asJson.delete(__ \ 'id) match {
            case _: JsUndefined => reward.asJson
            case js: JsValue => js
          }

          val update = Json.obj("rewards" -> JsArray(rewards.patch(
            index, Seq(rewards(index).as[JsObject] ++ updatedReward.as[JsObject]), 1
          ))).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false); Some(rewards(index).toPublic.as[Reward])
            case _ => throw StaleObject(project.get(__ \ '_id).as[String], collectionName)
          }
        } else Future.failed(DuplicateKey("reward.pledgeAmount", collectionName))
      } else Future.successful(None)
    }

    def removeReward(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("rewards" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty => removeReward(seq.head, index)
        case _ => Future.successful(None)
      }
    }

    def removeRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate): Future[Option[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("fundingInfo" -> 1, "rewards" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          implicit val precision = seq.head.get(__ \ 'fundingInfo \ 'currency) match {
            case _: JsUndefined => Precision(0.00000001) // should never happen
            case js: JsValue => Precision(if (js.as[JsString].value == PayGateway.Cryptocurrency) 0.00000001 else 0.01) 
          }

          val index = seq.head.get(__ \ 'rewards) match {
            case _: JsUndefined => -1
            case js: JsValue => js.as[JsArray].value.map { reward =>
              reward.get(__ \ 'pledgeAmount).as[Double]
            } indexOf(pledgeAmount ~~)
          }
          removeReward(seq.head, index)
        case _ => Future.successful(None)
      }
    }

    private def removeReward(project: JsValue, index: Int): Future[Option[Reward]] = {
      val rewards = project.get(__ \ 'rewards) match {
        case _: JsUndefined => Seq[JsValue]()
        case js: JsValue => js.as[JsArray].value
      }

      if (index > -1 && index < rewards.length) {
        val selector = Json.obj(
          "_id" -> project \ "_id",
          s"rewards.$index" -> rewards(index)
        )

        val update = { if (rewards.length > 1) {
          project.delete(__ \ '_id).delete((__ \ 'rewards)(index)).toUpdate
        } else {
          Json.obj("$unset" -> Json.obj("rewards" -> JsNull))
        }} ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

        db.findAndUpdate(selector, update, None).map {
          case Some(old) => version(old, false); Some(rewards(index).toPublic.as[Reward])
          case _ => throw StaleObject(project.get(__ \ '_id).as[String], collectionName)
        }
      } else Future.successful(None)
    }

    def findReward(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj(
          "rewards" -> 1,
          "rewards" -> Json.obj("$slice" -> Json.arr(index, 1))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "rewards" match {
        case _: JsUndefined => None
        case js: JsValue => js.toPublic.as[Seq[Reward]].headOption
      }}}
    }

    def findRewardById(
      projectId: Id, rewardId: Id
    )(implicit state: Ztate): Future[Option[Reward]] = {
      ReactiveMongoPlugin.db.command(RawCommand(
        Json.obj("aggregate" -> collectionName, "pipeline" -> Json.arr(
          Json.obj("$unwind" -> "$rewards"),
          Json.obj("$match" -> projectId.asJson.fromPublic
            .set(__ \ "state.value" -> JsString(state))
            .set(__ \ "rewards._id" -> Json.obj("$oid" -> rewardId.value))
          ),
          Json.obj("$group" -> Json.obj(
            "_id" -> 0,
            "reward" -> Json.obj("$first" -> "$rewards")
          )),
          Json.obj("$project" -> Json.obj(
            "_id" -> "$reward._id",
            "pledgeAmount" -> "$reward.pledgeAmount",
            "description" -> "$reward.description",
            "estimatedDeliveryDate" -> "$reward.estimatedDeliveryDate",
            "shipping" -> "$reward.shipping",
            "selectCount" -> "$reward.selectCount",
            "availableCount" -> "$reward.availableCount"
          ))
        )).toBson
      )).map { result =>
        (result.toJson \ "result").as[scala.collection.immutable.List[JsValue]] match {
          case seq if seq.nonEmpty => Some(seq.head.toPublic.as[Reward])
          case _ => None
        }
      }.recover {
        case e: LastError => throw DatabaseError(collectionName, e)
      }
    }

    def findRewards(
      projectId: Id
    )(implicit state: Ztate): Future[Seq[Reward]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("rewards" -> 1)),
        None,
        0, 1
      ).map { _.headOption.map { _ \ "rewards" match {
        case _: JsUndefined => Seq()
        case js: JsValue => js.toPublic.as[Seq[Reward]]
      }} getOrElse Seq() }
    }

    def selectRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate): Future[Option[Reward]] = {
      ReactiveMongoPlugin.db.command(RawCommand(
        Json.obj("aggregate" -> collectionName, "pipeline" -> Json.arr(
          Json.obj("$unwind" -> "$rewards"),
          Json.obj("$sort" -> Json.obj("rewards.pledgeAmount" -> -1)),
          Json.obj("$match" -> projectId.asJson.fromPublic
            .set(__ \ "state.value" -> JsString(state))
            .set(__ \ "rewards.pledgeAmount" -> Json.obj("$lte" -> pledgeAmount))
          ),
          Json.obj("$group" -> Json.obj(
            "_id" -> 0,
            "reward" -> Json.obj("$first" -> "$rewards")
          )),
          Json.obj("$project" -> Json.obj(
            "_id" -> "$reward._id",
            "pledgeAmount" -> "$reward.pledgeAmount",
            "description" -> "$reward.description",
            "estimatedDeliveryDate" -> "$reward.estimatedDeliveryDate",
            "shipping" -> "$reward.shipping",
            "selectCount" -> "$reward.selectCount",
            "availableCount" -> "$reward.availableCount"
          ))
        )).toBson
      )).flatMap { result =>
        (result.toJson \ "result").as[scala.collection.immutable.List[JsValue]] match {
          case seq if seq.nonEmpty =>
            val reward = seq.head.toPublic.as[Reward]
            db.findAndUpdate(
              projectId.asJson.fromPublic
                .set(__ \ "state.value" -> JsString(state))
                .set(__ \ "rewards._id" -> Json.obj("$oid" -> reward.id)),
              Json.obj("$inc" -> Json.obj("rewards.$.selectCount" -> 1)),
              None
            ).map { _ => Some(reward) }
          case _ => Future.successful(None)
        }
      }.recover {
        case e: LastError => throw DatabaseError(collectionName, e)
      }
    }

    def addFaq(
      projectId: Id, faq: Faq
    )(implicit state: Ztate): Future[Option[Int]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("faqs" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val faqs = seq.head.get(__ \ 'faqs) match {
            case _: JsUndefined => Seq[JsValue]()
            case js: JsValue => js.as[JsArray].value
          }

          var selector = Json.obj("_id" -> seq.head \ "_id")
          if (faqs.length > 0) selector = selector ++ Json.obj("faqs" -> faqs)

          val update = Json.obj(
            "faqs" -> (faqs :+ faq.asJson)
          ).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

          db.findAndUpdate(selector, update, None).map {
            case Some(old) => version(old, false); Some(faqs.length)
            case _ => throw StaleObject(projectId.value.get, collectionName)
          }
        case _ => Future.successful(None)
      }
    }

    def updateFaq(
      projectId: Id, index: Int, faq: Faq
    )(implicit state: Ztate): Future[Option[Faq]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("faqs" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val faqs = seq.head.get(__ \ 'faqs) match {
            case _: JsUndefined => Seq[JsValue]()
            case js: JsValue => js.as[JsArray].value
          }

          if (index > -1 && index < faqs.length) {
            val selector = Json.obj(
              "_id" -> seq.head \ "_id",
              "faqs" -> faqs
            )

            val update = Json.obj("faqs" -> faqs.patch(
              index, Seq(faqs(index).as[JsObject] ++ faq.asJson.as[JsObject]), 1
            )).toUpdate ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

            db.findAndUpdate(selector, update, None).map {
              case Some(old) => version(old, false); Some(faqs(index).as[Faq])
              case _ => throw StaleObject(projectId.value.get, collectionName)
            }
          } else Future.successful(None)
        case _ => Future.successful(None)
      }
    }

    def removeFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Faq]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("faqs" -> 1)),
        None,
        0, 1
      ).flatMap {
        case seq if seq.nonEmpty =>
          val faqs = seq.head.get(__ \ 'faqs) match {
            case _: JsUndefined => Seq[JsValue]()
            case js: JsValue => js.as[JsArray].value
          }

          if (index > -1 && index < faqs.length) {
            val selector = Json.obj(
              "_id" -> seq.head \ "_id",
              s"faqs.$index" -> faqs(index)
            )

            val update = { if (faqs.length > 1) {
              seq.head.delete(__ \ '_id).delete((__ \ 'faqs)(index)).toUpdate
            } else {
              Json.obj("$unset" -> Json.obj("faqs" -> JsNull))
            }} ++ Json.obj("$inc" -> Json.obj("_version" -> 1))

            db.findAndUpdate(selector, update, None).map {
              case Some(old) => version(old, false); Some(faqs(index).as[Faq])
              case _ => throw StaleObject(projectId.value.get, collectionName)
            }
          } else Future.successful(None)
        case _ => Future.successful(None)
      }
    }

    def findFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Faq]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj(
          "faqs" -> 1,
          "faqs" -> Json.obj("$slice" -> Json.arr(index, 1))
        )),
        None,
        0, 1
      ).map { _.headOption.flatMap { _ \ "faqs" match {
        case _: JsUndefined => None
        case js: JsValue => js.as[Seq[Faq]].headOption
      }}}
    }

    def findFaqs(
      projectId: Id
    )(implicit state: Ztate): Future[Seq[Faq]] = {
      db.find(
        projectId.asJson.fromPublic.set(__ \ "state.value" -> JsString(state)),
        Some(Json.obj("faqs" -> 1)),
        None,
        0, 1
      ).map { _.headOption.map { _ \ "faqs" match {
        case _: JsUndefined => Seq()
        case js: JsValue => js.as[Seq[Faq]]
      }} getOrElse Seq() }
    }

    protected override def version(obj: JsValue, removed: Boolean): Future[LastError] = {
      if (wip) Future.successful(new LastError(true, None, None, None, None, 0, false))
      else super.version(obj, removed)
    }
  }
}
