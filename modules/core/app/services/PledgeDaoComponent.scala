/*#
  * @file PledgeDaoComponent.scala
  * @begin 20-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent.Future
import org.joda.time.DateTime
import play.api.libs.json.JsValue
import services.common.DaoComponent
import models.core.Pledge

/**
  * Defines functionality for accessing pledge data.
  */
trait PledgeDaoComponent extends DaoComponent[Pledge] {

  /**
    * Returns an instance of an `PledgeDao` implementation.
    */
  def dao: PledgeDao

  /**
    * Represents a pledge data access object.
    */
  trait PledgeDao extends Dao {

    import pledges.PledgeFsm._

    /**
      * Returns the totals pledged by currency.
      *
      * @param state  The state of the pledges to sum.
      * @param timeUs The time up to which pledges are considered.
      * @return       A `Future` value containing the totals pledged by currency.
      */
    def totals(state: Ztate)(implicit timeUs: DateTime): Future[Map[String, Double]]

    /**
      * Returns the totals pledged by currency.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing the totals pledged by currency.
      */
    def totals(selector: JsValue): Future[Map[String, Double]]
  }
}
