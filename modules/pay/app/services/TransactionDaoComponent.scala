/*#
  * @file TransactionDaoComponent.scala
  * @begin 8-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import scala.concurrent.Future
import services.common.DaoComponent
import models.pay.Transaction

/**
  * Defines functionality for accessing transaction data.
  */
trait TransactionDaoComponent extends DaoComponent[Transaction] {

  /**
    * Returns an instance of a `TransactionDao` implementation.
    */
  def dao: TransactionDao

  /**
    * Represents a transaction data access object.
    */
  trait TransactionDao extends Dao {
  }
}
