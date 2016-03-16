/*#
  * @file PledgeDaoServiceComponent.scala
  * @begin 20-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.JsValue
import services.common.DefaultDaoServiceComponent
import models.core.Pledge

/**
  * Implements a `DaoServiceComponent` that provides access to pledge data.
  */
trait PledgeDaoServiceComponent extends DefaultDaoServiceComponent[Pledge] {
  this: PledgeDaoComponent =>

  /**
    * Returns an instance of an `PledgeDaoService` implementation.
    */
  override def daoService = new PledgeDaoService

  class PledgeDaoService extends DefaultDaoService {

    import pledges.PledgeFsm._

    def totals(state: Ztate)(implicit timeUs: DateTime = DateTime.now(DateTimeZone.UTC)) = dao.totals(state)
    def totals(selector: JsValue) = dao.totals(selector)
  }
}
