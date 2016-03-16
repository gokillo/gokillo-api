/*#
  * @file Contact.scala
  * @begin 8-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

/**
  * Represents an email contact.
  *
  * @constructor    Creates a new instance of the [[Contact]] class.
  * @param name     The full name of the contact.
  * @param email    The email address of the contact.
  * @param role     The job title of the contact.
  * @param phone    The phone number of the contact.
  * @param mobile   The mobile number of the contact.
  * @param isPublic A Boolean value indicating whether or not the
  *                 contact is publicly visible. 
  */
class Contact private(
  val name: String,
  val email: String,
  val role: Option[String],
  val phone: Option[String],
  val mobile: Option[String],
  val isPublic: Boolean
) {

  /**
    * Gets the email address of the contact with display name.
    *
    * @return The email address of the contact with display name
    *         (i.e. `Full Name <name@domain.com>`) if `name` is
    *         neither `null` nor empty; otherwise simply `email`.
    */
  def namedEmail = if (name != null && name.length > 0) s"$name <$email>" else email
}

/**
  * Factory class for creating [[Contact]] instances.
  */
object Contact {

  /**
    * Creates a new instance of the [[Contact]] class.
    *
    * @param namedEmail The email address of the contact with display
    *                   name (i.e. `Full Name <name@domain.com>`).
    * @param role       The job title of the contact.
    * @return           A new instance of the [[Contact]] class.
    */
  def apply(namedEmail: String, role: Option[String]): Contact = {
    apply(namedEmail, role, None, None, false)
  }

  /**
    * Creates a new instance of the [[Contact]] class.
    *
    * @param namedEmail The email address of the contact with display
    *                   name (i.e. `Full Name <name@domain.com>`).
    * @param role       The job title of the contact.
    * @param phone      The phone number of the contact.
    * @param mobile     The mobile number of the contact.
    * @param isPublic   A Boolean value indicating whether or not the
    *                   contact is publicly visible. 
    * @return           A  new instance of the [[Contact]] class.
    * @note             If `namedEmail` contains a canonical email
    *                   address (i.e. `local-part@domain`) then the
    *                   local-part is used instead.
    */
  def apply(
    namedEmail: String,
    role: Option[String],
    phone: Option[String],
    mobile: Option[String],
    isPublic: Boolean): Contact = {

    var name: String = null
    var email: String = null

    if (namedEmail != null && !namedEmail.trim.isEmpty) {
      """(?:"?([^"]*)"?\s)?(?:<?(.+@[^>]+)>?)""".r.findAllIn(
        namedEmail
      ).matchData foreach { m =>
        name = m.group(1); email = m.group(2)
        if (name == null) {
          name = namedEmail.split('@')(0).split('.').map(_.capitalize).mkString(" ")
        }
      }
    }

    apply(name, email, role, phone, mobile, isPublic)
  }

  /**
    * Creates a new instance of the [[Contact]] class.
    *
    * @param name     The full name of the contact.
    * @param email    The email address of the contact without display
    *                 name (i.e. `name@domain.com`).
    * @param role     The job title of the contact.
    * @param phone    The phone number of the contact.
    * @param mobile   The mobile number of the contact.
    * @param isPublic A Boolean value indicating whether or not the
    *                 contact is publicly visible. 
    * @return         A new instance of the [[Contact]] class.
    */
  def apply(
    name: String,
    email: String,
    role: Option[String] = None,
    phone: Option[String] = None,
    mobile: Option[String] = None,
    isPublic: Boolean = false) = {

    new Contact(name, email, role, phone, mobile, isPublic)
  }
}
