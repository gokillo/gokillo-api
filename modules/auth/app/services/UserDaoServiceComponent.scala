/*#
  * @file UserDaoServiceComponent.scala
  * @begin 3-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.DefaultDaoServiceComponent
import models.common.{Address, Id}
import models.auth.User

/**
  * Implements a `DaoServiceComponent` that provides access to user data.
  */
trait UserDaoServiceComponent extends DefaultDaoServiceComponent[User] {
  this: UserDaoComponent =>

  /**
    * Returns an instance of an `UserDaoService` implementation.
    */
  override def daoService = new UserDaoService

  class UserDaoService extends DefaultDaoService {

    def findByAccountId(accountId: Id, ifPublic: Boolean = true) = dao.findByAccountId(accountId, ifPublic)
    def addAddress(userId: Id, address: Address) = dao.addAddress(userId, address)
    def updateAddress(userId: Id, index: Int, address: Address) = dao.updateAddress(userId, index, address)
    def updateAddressByName(userId: Id, name: String, address: Address) = dao.updateAddressByName(userId, name, address)
    def updateDefaultAddress(userId: Id, address: Address) = dao.updateDefaultAddress(userId, address)
    def setDefaultAddress(userId: Id, index: Int) = dao.setDefaultAddress(userId, index)
    def setDefaultAddressByName(userId: Id, name: String) = dao.setDefaultAddressByName(userId, name)
    def removeAddress(userId: Id, index: Int) = dao.removeAddress(userId, index)
    def removeAddressByName(userId: Id, name: String) = dao.removeAddressByName(userId, name)
    def findAddress(userId: Id, index: Int) = dao.findAddress(userId, index)
    def findAddressByName(userId: Id, name: String) = dao.findAddressByName(userId, name)
    def findDefaultAddress(userId: Id) = dao.findDefaultAddress(userId)
    def findAddresses(userId: Id) = dao.findAddresses(userId)
    def activateAccount(userId: Id, accountId: Id) = dao.activateAccount(userId, accountId)
    def renameAccount(userId: Id, index: Int, newName: String) = dao.renameAccount(userId, index, newName)
    def renameAccountById(userId: Id, accountId: Id, newName: String) = dao.renameAccountById(userId, accountId, newName)
    def renameAccountByName(userId: Id, name: String, newName: String) = dao.renameAccountByName(userId, name, newName)
    def renameDefaultAccount(userId: Id, newName: String) = dao.renameDefaultAccount(userId, newName)
    def setDefaultAccount(userId: Id, index: Int) = dao.setDefaultAccount(userId, index)
    def setDefaultAccountById(userId: Id, accountId: Id) = dao.setDefaultAccountById(userId, accountId)
    def setDefaultAccountByName(userId: Id, name: String) = dao.setDefaultAccountByName(userId, name)
    def findAccount(userId: Id, index: Int) = dao.findAccount(userId, index)
    def findAccountById(userId: Id, accountId: Id) = dao.findAccountById(userId, accountId)
    def findAccountByName(userId: Id, name: String) = dao.findAccountByName(userId, name)
    def findDefaultAccount(userId: Id) = dao.findDefaultAccount(userId)
    def findAccounts(userId: Id) = dao.findAccounts(userId)
  }
}
