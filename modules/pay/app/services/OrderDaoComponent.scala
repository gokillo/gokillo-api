/*#
  * @file OrderDaoComponent.scala
  * @begin 9-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import scala.concurrent.Future
import play.api.libs.json._
import services.common.DaoComponent
import models.common.Id
import models.pay.Order

/**
  * Defines functionality for accessing order data.
  */
trait OrderDaoComponent extends DaoComponent[Order] {

  /**
    * Returns an instance of an `OrderDao` implementation.
    */
  def dao: OrderDao

  /**
    * Represents an order data access object.
    */
  trait OrderDao extends Dao {

    import utils.common.typeExtensions.Precision

    /**
      * Finds the root of the order identified by the specified id.
      *
      * @param id The id that identifies the order to find the root for.
      * @return   A `Future` value containing the root of the order identified
      *           by `id`, or `None` if the root could not be found.
      */
    def findRoot(id: Id): Future[Option[Order]]

    /**
      * Finds the orders that match the specified criteria by applying the
      * specified precision to the amount, if defined.
      *
      * @param selector   The selector object.
      * @param projection An `Option` containing the projection object, or
      *                   `None` if no projection is required.
      * @param sort       An `Option` containing the sorting object, or
      *                   `None` if no sorting is required.
      * @param page       The page to retrieve, 0-based.
      * @param perPage    The number of results per page.
      * @param precision  The precision to apply to `amount`.
      * @return           A `Future` value containing a `Seq` of entities
      *                   that match `selector`, sorted by `sort`.
      */ 
    def findWithPrecision(
      selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int
    )(implicit precision: Precision): Future[Seq[Order]]
  }
}
