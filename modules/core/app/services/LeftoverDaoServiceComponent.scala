/*#
  * @file LeftoverDaoServiceComponent.scala
  * @begin 6-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import services.common.DefaultDaoServiceComponent
import models.core.Leftover
import models.pay.Coin

/**
  * Implements a `DaoServiceComponent` that provides access to leftover data.
  */
trait LeftoverDaoServiceComponent extends DefaultDaoServiceComponent[Leftover] {
  this: LeftoverDaoComponent =>

  /**
    * Returns an instance of a `LeftoverDaoService` implementation.
    */
  override def daoService = new LeftoverDaoService

  class LeftoverDaoService extends DefaultDaoService {

    def incAmount(by: Coin) = dao.incAmount(by)
    def reset = dao.reset
    def findCurrent = dao.findCurrent
    def findWithdrawn(page: Int, perPage: Int) = dao.findWithdrawn(page, perPage)
  }
}
