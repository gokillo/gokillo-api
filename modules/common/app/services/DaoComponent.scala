/*#
  * @file DaoComponent.scala
  * @begin 14-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import scala.concurrent.Future
import play.api.libs.json.JsValue
import utils.common.TypeFactory
import models.common.{JsEntity, Id}

/**
  * Defines functionality for data access objects.
  * @tparam A The type this `DaoComponent` provides access for.
  */
trait DaoComponent[A <: JsEntity] {

  /**
    * Type factory for creating new instances of `A`.
    */
  protected val typeFactory: TypeFactory[A]

  /**
    * Returns a new instance of `A` created from the specified JSON.
    *
    * @param json The JSON from which to create a new instance of `A`.
    * @return     A new instance of `A` created from `json`.
    * @note       `box` is an alias for `typeFactory.newInstance`.
    */
  protected def box(json: JsValue): A = typeFactory.newInstance(json)()

  /**
    * Returns an instance of a `Dao` implementation.
    */
  def dao: Dao

  /**
    * Represents a data access object.
    */
  trait Dao {

    /**
      * Gets the name of the underlying datastore.
      */
    def collectionName: String

    /**
      * Generates an id that uniquely identifies an entity.
      * @return An entity id.
      */
    def generateId: String

    /**
      * Returns the number of documents that match the specified criteria.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing the number of documents
      *                 that match `selector`.
      */
    def count(selector: JsValue): Future[Int]

    /**
      * Returns the distinct values that match the specified critera.
      *
      * @param field    The field to find the distinct values for.
      * @param selector The selector object.
      * @return         A `Future` value containing the distinct values
      *                 as a sequence of strings.
      */
    def distinct(field: String, selector: JsValue): Future[Seq[String]]

    /**
      * Inserts the specified entity.
      *
      * @param entity The entity to insert.
      * @return       A `Future` value containing the inserted entity.
      */
    def insert(entity: A): Future[A]

    /**
      * Updates the specified entity.
      *
      * @param entity The entity to update.
      * @return       A `Future` value containing `true` if the entity was
      *               updated, or `false` if it could not be found.
      */
    def update(entity: A): Future[Boolean]

    /**
      * Updates the entities that match the specified criteria.
      *
      * @param selector The selector object.
      * @param update   The update object.
      * @return         A `Future` value containing the number
      *                 of entities affected by the update.
      */
    def update(selector: JsValue, update: JsValue): Future[Int]

    /**
      * Removes the entity identified by the specified id.
      *
      * @param id The id that identifies the entity to remove.
      * @return   A `Future` value containing `true` if the entity was
      *           removed, or `false` if it could not be found.
      */
    def remove(id: Id): Future[Boolean]

    /**
      * Removes the entities that match the specified criteria.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing the number of
      *                 entities removed.
      */
    def remove(selector: JsValue): Future[Int]

    /**
      * Finds the entity identified by the specified id.
      *
      * @param id         The id that identifies the entity to find.
      * @param projection An `Option` containing the projection object, or
      *                   `None` if no projection is required.
      * @return           A `Future` value containing the entity identified
      *                   by `id`, or `None` if it could not be found.
      */
    def find(id: Id, projection: Option[JsValue]): Future[Option[A]]

    /**
      * Finds the entities that match the specified criteria.
      *
      * @param selector   The selector object.
      * @param projection An `Option` containing the projection object, or
      *                   `None` if no projection is required.
      * @param sort       An `Option` containing the sorting object, or
      *                   `None` if no sorting is required.
      * @param page       The page to retrieve, 0-based.
      * @param perPage    The number of results per page.
      * @return           A `Future` value containing a `Seq` of entities
      *                   that match `selector`, sorted by `sort`.
      */
    def find(selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int): Future[Seq[A]]

    /**
      * Finds and updates the specified entity.
      *
      * @param entity The entity to find and update.
      * @return       A `Future` value containing the old entity, or
      *               `None` if it could not be found.
      */
    def findAndUpdate(entity: A): Future[Option[A]]

    /**
      * Finds and updates the first entity that matches the specified criteria.
      *
      * @param selector The selector object.
      * @param update   The update object.
      * @param sort     An `Option` containing the sorting object, or
      *                 `None` if no sorting is required.
      * @return         A `Future` value containing the old entity, or
      *                 `None` if it could not be found.
      */
    def findAndUpdate(selector: JsValue, update: JsValue, sort: Option[JsValue]): Future[Option[A]]

    /**
      * Finds and removes the entity identified by the specified id.
      *
      * @param id The id that identifies the entity to remove.
      * @return   A `Future` value containing the removed entity, or
      *           `None` if it could not be found.
      */
    def findAndRemove(id: Id): Future[Option[A]]

    /**
      * Finds and removes the first entity that matches the specified criteria.
      *
      * @param selector The selector object.
      * @param sort     An `Option` containing the sorting object, or
      *                 `None` if no sorting is required.
      * @return         A `Future` value containing the removed entity, or
      *                 `None` if it could not be found.
      */
    def findAndRemove(selector: JsValue, sort: Option[JsValue]): Future[Option[A]]
  }
}
