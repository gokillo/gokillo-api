/*#
  * @file FeeDaoComponent.scala
  * @begin 30-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent.Future
import services.common.DaoComponent
import models.core.Fee

/**
  * Defines functionality for accessing fee data.
  */
trait FeeDaoComponent extends DaoComponent[Fee] {

  /**
    * Returns an instance of an `FeeDao` implementation.
    */
  def dao: FeeDao

  /**
    * Represents a fee data access object.
    */
  trait FeeDao extends Dao {
  }
}
