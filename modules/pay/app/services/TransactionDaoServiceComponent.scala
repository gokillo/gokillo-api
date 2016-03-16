/*#
  * @file TransactionDaoServiceComponent.scala
  * @begin 8-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import services.common.DefaultDaoServiceComponent
import models.pay.Transaction

/**
  * Implements a `DaoServiceComponent` that provides access to transaction data.
  */
trait TransactionDaoServiceComponent extends DefaultDaoServiceComponent[Transaction] {
  this: TransactionDaoComponent =>

  /**
    * Returns an instance of a `TransactionDaoService` implementation.
    */
  override def daoService = new TransactionDaoService

  class TransactionDaoService extends DefaultDaoService {
  }
}
