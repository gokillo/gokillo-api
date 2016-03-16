/*#
  * @file TechUsersRegistry.scala
  * @begin 4-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.collection.mutable.Map

/**
  * Implements a registry of technical users.
  * @note To be implemented.
  */
object TechUsersRegistry {

  private val techUsers: Map[String, String] = Map().withDefault(_ => "0" * 24)

  /**
    * Returns the account identifier associated with the specified username.
    * @param username The username to get the account id for.
    * @return   The account id associated with `username`.
    * @note     Not implemented yet and always returns `000000000000000000000000`.
    */
  def accountId(username: String) = techUsers(username)
}
