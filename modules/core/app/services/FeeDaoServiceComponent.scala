/*#
  * @file FeeDaoServiceComponent.scala
  * @begin 30-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import services.common.DefaultDaoServiceComponent
import models.core.Fee

/**
  * Implements a `DaoServiceComponent` that provides access to fee data.
  */
trait FeeDaoServiceComponent extends DefaultDaoServiceComponent[Fee] {
  this: FeeDaoComponent =>

  /**
    * Returns an instance of a `FeeDaoService` implementation.
    */
  override def daoService = new FeeDaoService

  class FeeDaoService extends DefaultDaoService {}
}
