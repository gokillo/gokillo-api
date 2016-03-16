/*#
  * @file AccountDaoComponent.scala
  * @begin 19-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent.Future
import services.common.DaoComponent
import models.common.Id
import models.auth.Account
import models.auth.Role._

/**
  * Defines functionality for accessing account data.
  */
trait AccountDaoComponent extends DaoComponent[Account] {

  /**
    * Returns an instance of an `AccountDao` implementation.
    */
  def dao: AccountDao

  /**
    * Represents an account data access object.
    */
  trait AccountDao extends Dao {

    /**
      * Adds the specified roles to the account identified by the specified id.
      *
      * @param accountId  The id that identifies the account to add the roles to.
      * @param roles      The roles to add.
      */
    def addRoles(accountId: Id, roles: List[Role]): Future[Unit]

    /**
      * Removes the specified roles from the account identified by the specified id.
      *
      * @param accountId  The id that identifies the account to remove the roles from.
      * @param roles      The roles to remove.
      */
    def removeRoles(accountId: Id, roles: List[Role]): Future[Unit]
  }
}
