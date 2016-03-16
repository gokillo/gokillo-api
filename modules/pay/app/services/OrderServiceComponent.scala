/*#
  * @file OrderDaoServiceComponent.scala
  * @begin 9-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import scala.concurrent.Future
import play.api.libs.json._
import services.common.DefaultDaoServiceComponent
import models.common.Id
import models.pay.Order

/**
  * Implements a `DaoServiceComponent` that provides access to order data.
  */
trait OrderDaoServiceComponent extends DefaultDaoServiceComponent[Order] {
  this: OrderDaoComponent =>

  /**
    * Returns an instance of an `OrderDaoService` implementation.
    */
  override def daoService = new OrderDaoService

  class OrderDaoService extends DefaultDaoService {

    import utils.common.typeExtensions._

    def findRoot(id: Id): Future[Option[Order]] = dao.findRoot(id)

    def findWithPrecision(
      selector: JsValue, projection: Option[JsValue], sort: Option[JsValue],
      page: Int, perPage: Int
    )(implicit precision: Precision = Precision(0.00000001)): Future[Seq[Order]] = dao.findWithPrecision(
      selector, projection, sort, page, perPage
    )
  }
}
