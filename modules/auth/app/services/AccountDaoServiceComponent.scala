/*#
  * @file AccountDaoServiceComponent.scala
  * @begin 19-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.DefaultDaoServiceComponent
import models.common.Id
import models.auth.Account
import models.auth.Role._

/**
  * Implements a `DaoServiceComponent` that provides access to account data.
  */
trait AccountDaoServiceComponent extends DefaultDaoServiceComponent[Account] {
  this: AccountDaoComponent =>

  /**
    * Returns an instance of an `AccountDaoService` implementation.
    */
  override def daoService = new AccountDaoService

  class AccountDaoService extends DefaultDaoService {

    def addRoles(accountId: Id, roles: List[Role]) = dao.addRoles(accountId, roles)
    def removeRoles(accountId: Id, roles: List[Role]) = dao.removeRoles(accountId, roles)
  }
}
