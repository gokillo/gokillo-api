/*#
  * @file LeftoverDaoComponent.scala
  * @begin 6-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent.Future
import services.common.DaoComponent
import models.core.Leftover
import models.pay.Coin

/**
  * Defines functionality for accessing leftover data.
  */
trait LeftoverDaoComponent extends DaoComponent[Leftover] {

  /**
    * Returns an instance of an `LeftoverDao` implementation.
    */
  def dao: LeftoverDao

  /**
    * Represents a leftover data access object.
    */
  trait LeftoverDao extends Dao {

    /**
      * Increments the current leftover by the specified amount.
      *
      * @param by The amount by which to increment the current leftover.
      * @return   A `Future` value containing the old amount.
      */
    def incAmount(by: Coin): Future[Coin]

    /**
      * Sets the current leftover as withdrawn so that the next
      * increment restarts from zero.
      *
      * @return A `Future` value containing the leftover withdrawn,
      *         or `None` if there is no leftover to withdraw.
      */
    def reset: Future[Option[Leftover]]

    /**
      * Finds the current leftover.
      *
      * @return A `Future` value containing the current leftover,
      *         or `None` if it could not be found.
      */
    def findCurrent: Future[Option[Leftover]]

    /**
      * Finds the leftovers withdrawn so far.
      *
      * @param page       The page to retrieve, 0-based.
      * @param perPage    The number of results per page.
      * @return           A `Future` value containing a `Seq` of leftovers.
      */
    def findWithdrawn(page: Int, perPage: Int): Future[Seq[Leftover]]
  }
}
