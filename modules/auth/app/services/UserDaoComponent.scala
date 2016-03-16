/*#
  * @file UserDaoComponent.scala
  * @begin 3-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent.Future
import services.common.DaoComponent
import models.common.{Address, Id}
import models.auth.{User, MetaAccount}

/**
  * Defines functionality for accessing user data.
  */
trait UserDaoComponent extends DaoComponent[User] {

  /**
    * Returns an instance of an `UserDao` implementation.
    */
  def dao: UserDao

  /**
    * Represents a user data access object.
    */
  trait UserDao extends Dao {

    /**
      * Finds the user that owns the account identified by the specified id.
      *
      * @param accountId  The id that identifies the account to find the user for.
      * @param ifPublic   A Boolean value indicating whether to find the user only if public.
      * @return       A `Future` value containing the user that owns the account
      *               identified by `accountId`, or `None` if the user could not
      *               be found.
      */
    def findByAccountId(accountId: Id, ifPublic: Boolean): Future[Option[User]]

    /**
      * Adds the specified address to the user identified by the specified
      * id.
      *
      * @param userId The id that identifies the user to add the address to.
      *               The address to add.
      * @return       A `Future` value containing the zero-based index of the added
      *               address, or `None` if the user identified by `userId` could
      *               not be found.
      */
    def addAddress(userId: Id, address: Address): Future[Option[Int]]

    /**
      * Updates the address at the specified index of the user identified
      * by the specified id.
      *
      * @param userId   The id that identifies the user to update the address for.
      * @param index    The index of the address to update.
      * @param address  The address update.
      * @return         A `Future` value containing the old value of the address
      *                 at `index`, or `None` if the user or the address could
      *                 not be found.
      */
    def updateAddress(userId: Id, index: Int, address: Address): Future[Option[Address]]

    /**
      * Updates the address with the specified name of the user identified
      * by the specified id.
      *
      * @param userId   The id that identifies the user to update the address for.
      * @param name     The name of the address to update.
      * @param address  The address update.
      * @return         A `Future` value containing the old value of the address
      *                 named `name`, or `None` if the user or the address could
      *                 not be found.
      */
    def updateAddressByName(userId: Id, name: String, address: Address): Future[Option[Address]]

    /**
      * Updates the default address of the user identified by the specified id.
      *
      * @param userId   The id that identifies the user to update the default address for.
      * @param address  The address update.
      * @return         A `Future` value containing the old value of the default address,
      *                 or `None` if the user could not be found.
      */
    def updateDefaultAddress(userId: Id, address: Address): Future[Option[Address]]

    /**
      * Sets the address at the specified index as the default address of the
      * user identified by the specified id.
      *
      * @param userId The id that identifies the user to set the default
      *               address for.
      * @param index  The index of the address to set as the default.
      * @return       A `Future` value containing the address set as the default,
      *               or `None` if the user or the address could not be found.
      */
    def setDefaultAddress(userId: Id, index: Int): Future[Option[Address]]

    /**
      * Sets the address with the specified name as the default address of the
      * user identified by the specified id.
      *
      * @param userId The id that identifies the user to set the default
      *               address for.
      * @param name   The name of the address to set as the default.
      * @return       A `Future` value containing the address set as the default,
      *               or `None` if the user or the address could not be found.
      */
    def setDefaultAddressByName(userId: Id, name: String): Future[Option[Address]]

    /**
      * Removes the address at the specified index of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to remove the address for.
      * @param index  The index of the address to remove.
      * @return       A `Future` value containing the removed address of the user
      *               identified by `userId`, or `None` if the user or the address
      *               could not be found.
      */
    def removeAddress(userId: Id, index: Int): Future[Option[Address]]

    /**
      * Removes the address with the specified name of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to remove the address for.
      * @param name   The name of the address to remove.
      * @return       A `Future` value containing the removed address of the user
      *               identified by `userId`, or `None` if the user or the address
      *               could not be found.
      */
    def removeAddressByName(userId: Id, name: String): Future[Option[Address]]

    /**
      * Finds the address at the specified index of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to find the address for.
      * @param index  The index of the address to find.
      * @return       A `Future` value containing the address at `index` of
      *               the user identified by `userId`, or `None` if the user
      *               or the address could not be found.
      */
    def findAddress(userId: Id, index: Int): Future[Option[Address]]

    /**
      * Finds the address with the specified name of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to find the address for.
      * @param name   The name of the address to find.
      * @return       A `Future` value containing the address named `name` of
      *               the user identifed by `userId`, or `None` if the user
      *               or the address could not be found.
      */
    def findAddressByName(userId: Id, name: String): Future[Option[Address]]

    /**
      * Finds the default address of the user identified by the specified id.
      *
      * @param userId The id that identifies the user to find the default
      *               address for.
      * @return       A `Future` value containing the default address of the
      *               user identified by `userId`, or `None` if the user
      *               could not be found.
      */
    def findDefaultAddress(userId: Id): Future[Option[Address]]

    /**
      * Finds the addresses of the user identified by the specified id.
      *
      * @param userId The id that identifies the user to find the addresses for.
      * @return       A `Future` value containing the addresses of the user
      *               identified by `userId`, or an empty `Seq` if the user
      *               could not be found or no addresses could be found.
      */
    def findAddresses(userId: Id): Future[Seq[Address]]

    /**
      * Activates the account identified by the specified id for the user
      * identified by the specified id.
      *
      * @param userId     The id that identifies the user to activate the account for.
      * @param accountId  The id that identifies the account to activate.
      * @return           A `Future` value containing the activated account, or `None`
      *                   if the user or the account could not be found.
      */
    def activateAccount(userId: Id, accountId: Id): Future[Option[MetaAccount]]

    /**
      * Renames the account at the specified index of the user identified
      * by the specified id.
      *
      * @param userId   The id that identifies the user to rename the account for.
      * @param index    The index of the account to rename.
      * @param newName  The new name of the account.
      * @return         A `Future` value containing the renamed account, or `None`
      *                 if the user or the account could not be found.
      */
    def renameAccount(userId: Id, index: Int, newName: String): Future[Option[MetaAccount]]

    /**
      * Renames the account identified by the specified id of the user identified
      * by the specified id.
      *
      * @param userId     The id that identifies the user to rename the account for.
      * @param accountId  The id that identifies the account to rename.
      * @param newName    The new name of the account.
      * @return           A `Future` value containing the renamed account, or `None`
      *                   if the user or the account could not be found.
      */
    def renameAccountById(userId: Id, accountId: Id, newName: String): Future[Option[MetaAccount]]

    /**
      * Renames the accounts with the specified name of the user identified
      * by the specified id.
      *
      * @param userId   The id that identifies the user to rename the account for.
      * @param name     The name of the account to rename.
      * @param newName  The new name of the account.
      * @return         A `Future` value containing the renamed account, or `None`
      *                 if the user or the account could not be found.
      */
    def renameAccountByName(userId: Id, name: String, newName: String): Future[Option[MetaAccount]]

    /**
      * Renames the default account of the user identified by the specified id.
      *
      * @param userId   The id that identifies the user to rename the default account for.
      * @param newName  The new name of the account.
      * @return         A `Future` value containing the renamed account, or `None`
      *                 if the user could not be found.
      */
    def renameDefaultAccount(userId: Id, newName: String): Future[Option[MetaAccount]]

    /**
      * Sets the account at the specified index as the default account of the
      * user identified by the specified id.
      *
      * @param userId The id that identifies the user to set the default
      *               account for.
      * @param index  The index of the account to set as the default.
      * @return       A `Future` value containing the account set as the default,
      *               or `None` if the user or the account could not be found.
      */
    def setDefaultAccount(userId: Id, index: Int): Future[Option[MetaAccount]]

    /**
      * Sets the account identified by the specified id as the default account of
      * the user identified by the specified id.
      *
      * @param userId     The id that identifies the user to set the default
      *                   account for.
      * @param accountId  The id that identifies the account to set as the default.
      * @return           A `Future` value containing the account set as the default,
      *                   or `None` if the user or the account could not be found.
      */
    def setDefaultAccountById(userId: Id, accountId: Id): Future[Option[MetaAccount]]

    /**
      * Sets the account with the specified name as the default account of the
      * user identified by the specified id.
      *
      * @param userId The id that identifies the user to set the default
      *               account for.
      * @param name   The name of the account to set as the default.
      * @return       A `Future` value containing the account set as the default,
      *               or `None` if the user or the account could not be found.
      */
    def setDefaultAccountByName(userId: Id, name: String): Future[Option[MetaAccount]]

    /**
      * Finds the account at the specified index of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to find the account for.
      * @param index  The index of the account to find.
      * @return       A `Future` value containing the account at `index` of the
      *               user identified by `userId`, or `None` if the user or
      *               the account could not be found.
      */
    def findAccount(userId: Id, index: Int): Future[Option[MetaAccount]]

    /**
      * Finds the account identified by the specified id of the user identified
      * by the specified id.
      *
      * @param userId     The id that identifies the user to find the account for.
      * @param accountId  The id that identifies the account to find.
      * @return           A `Future` value containing the account identifed by
      *                   `accountId` of the user identifed by `userId`, or `None`
      *                   if the user or the account could not be found.
      */
    def findAccountById(userId: Id, accountId: Id): Future[Option[MetaAccount]]

    /**
      * Finds the account with the specified name of the user identified
      * by the specified id.
      *
      * @param userId The id that identifies the user to find the account for.
      * @param name   The name of the account to find.
      * @return       A `Future` value containing the account named `name` of
      *               the user identifed by `userId`, or `None` if the user
      *               or the account could not be found.
      */
    def findAccountByName(userId: Id, name: String): Future[Option[MetaAccount]]

    /**
      * Finds the default account of the user identified by the specified id.
      *
      * @param userId The id that identifies the user to find the default
      *               account for.
      * @return       A `Future` value containing the default account of the
      *               user identified by `userId`, or `None` if the user
      *               could not be found.
      */
    def findDefaultAccount(userId: Id): Future[Option[MetaAccount]]

    /**
      * Finds the accounts of the user identified by the specified id.
      *
      * @param userId The id that identifies the user to find the accounts for.
      * @return       A `Future` value containing the accounts of the user
      *               identified by `userId`, or an empty `Seq` if the user
      *               could not be found.
      */
    def findAccounts(userId: Id): Future[Seq[MetaAccount]]
  }
}
