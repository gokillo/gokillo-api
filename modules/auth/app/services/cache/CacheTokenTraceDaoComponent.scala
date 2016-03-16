/*#
  * @file CacheTokenTraceDaoComponent.scala
  * @begin 10-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.cache

import scala.concurrent.Future
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.auth.TokenTraceDaoComponent
import utils.common.Formats._
import models.common.Id
import models.auth.{Token, TokenTrace}

/**
  * Implements the token trace DAO component for the public cache.
  */
trait CacheTokenTraceDaoComponent extends TokenTraceDaoComponent {

  protected val typeFactory = null

  override def dao = new CacheTokenTraceDao

  class CacheTokenTraceDao extends TokenTraceDao {

    def collectionName = "cache"

    def generateId: String = java.util.UUID.randomUUID.toString

    def count(selector: JsValue): Future[Int] = Future.failed(
      new UnsupportedOperationException
    )

    def distinct(field: String, selector: JsValue): Future[List[String]] = Future.failed(
      new UnsupportedOperationException
    )

    def insert(entity: TokenTrace): Future[TokenTrace] = Future {
      val expiration = entity.expirationTime.minus(DateTime.now(DateTimeZone.UTC).getMillis)
      Cache.set(entity.id.get, entity, (expiration.getMillis / 1000).asInstanceOf[Int])
      entity
    }

    def update(entity: TokenTrace): Future[Boolean] = findAndUpdate(
      entity
    ).map { _.isDefined }

    def update(selector: JsValue, update: JsValue): Future[Int] = findAndUpdate(
      selector,
      update,
      None
    ).map { success => if (success.isDefined) 1 else 0 }

    def remove(id: Id): Future[Boolean] = remove(
      id.asJson
    ).map { _ > 0 }

    def remove(selector: JsValue): Future[Int] = Future {
      (selector as (__ \ 'id).readNullable[String]) match {
        case Some(id) => Cache.get(id).map { _ => Cache.remove(id); 1 } getOrElse 0
        case _ => 0
      }
    }

    def removeExpired: Future[Int] = Future(0)

    def find(id: Id, projection: Option[JsValue]): Future[Option[TokenTrace]] = find(
      id.asJson,
      projection,
      None,
      0, 1
    ).map { _.headOption }

    def find(selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int): Future[Seq[TokenTrace]] = Future {
      (selector as (__ \ 'id).readNullable[String]) match {
        case Some(id) => Cache.getAs[TokenTrace](id).map(List(_)) getOrElse Seq.empty
        case _ => Seq.empty
      }
    }

    def findAndUpdate(entity: TokenTrace): Future[Option[TokenTrace]] = findAndUpdate(
      Json.obj("id" -> entity.id),
      Json.obj("expirationTime" -> entity.expirationTime),
      None
    )

    def findAndUpdate(token: Token): Future[Option[TokenTrace]] = findAndUpdate(
      Json.obj("id" -> token.id),
      Json.obj("expirationTime" -> token.expirationTime),
      None
    )

    def findAndUpdate(selector: JsValue, update: JsValue, sort: Option[JsValue]): Future[Option[TokenTrace]] = Future {
      (selector as (__ \ 'id).readNullable[String]) match {
        case Some(id) =>
          (update as (__ \ 'expirationTime).readNullable[DateTime]) match {
            case Some(expirationTime) =>
              val now = DateTime.now(DateTimeZone.UTC)
              if (expirationTime.isAfter(now)) {
                Cache.getAs[TokenTrace](id).map { oldTokenTrace =>
                  val newTokenTrace = TokenTrace(id, oldTokenTrace.username, expirationTime)
                  val expiration = expirationTime.minus(now.getMillis).getMillis / 1000
                  Cache.set(id, newTokenTrace, expiration.asInstanceOf[Int])
                  oldTokenTrace
                }
              } else None
            case _ => None
          }
        case _ => None
      }
    }

    def findAndRemove(id: Id): Future[Option[TokenTrace]] = findAndRemove(
      id.asJson,
      None
    )

    def findAndRemove(selector: JsValue, sort: Option[JsValue]): Future[Option[TokenTrace]] = Future {
      (selector as (__ \ 'id).readNullable[String]) match {
        case Some(id) =>
          Cache.getAs[TokenTrace](id).map { tokenTrace =>
            Cache.remove(id)
            tokenTrace
          }
        case _ => None
      }
    }
  }
}
