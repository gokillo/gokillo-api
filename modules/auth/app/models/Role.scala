/*#
  * @file Role.scala
  * @begin 22-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import services.common.CommonErrors._

/**
  * Defines subject roles.
  */
object Role extends Enumeration {

  type Role = Value

  /**
    * The subject has unspecified privileges.
    */
  val Any = Value(0)

  /**
    * The subject has all privileges.
    */
  val Superuser = Value(1)

  /**
    * The subject has browsing and publishing privileges.
    */
  val Auditor = Value(2)

  /**
    * The subject has browsing and editing privileges.
    */
  val Editor = Value(3)

  /**
    * The subject has browsing and funding privileges.
    */
  val Member = Value (4)

  /**
    * The subject has browsing privileges only.
    */
  val Guest = Value (5)

  private val roles = values.map { value =>
    (value.id, value.toString.toLowerCase)
  }.toMap.withDefaultValue("")

  /**
    * Gets a role id by name.
    *
    * @param name The name of the role.
    * @return     The role id, if `name` exists; otherwise, -1.
    */
  def id(name: String) = roles.map(_.swap).withDefaultValue(-1)(name)

  /**
    * Gets the name of a role by id.
    *
    * @param id The role id.
    * @return   The name of the role, if `id` exists; otherwise, an empty string.
    */
  def name(id: Int) = roles(id)

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): Role = {
    try {
      return Role.withName(name.toLowerCase.capitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("role", name)
    }
  }
}
