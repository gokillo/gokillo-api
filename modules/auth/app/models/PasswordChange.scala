/*#
  * @file PasswordChange.scala
  * @begin 14-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import play.api.libs.json._
import models.common.JsModel
import Password._

/**
  * Represents a password change.
  *
  * @constructor  Initializes a new instance of the [[PasswordChange]] class.
  * @param json   The password change data as JSON.
  */
class PasswordChange protected(
  protected var json: JsValue
) extends JsModel with api.PasswordChange {

  def currentPassword = json as (__ \ 'currentPassword).read[Password]
  def currentPassword_= (v: Password) = setValue((__ \ 'currentPassword), Json.toJson(v))
  def newPassword = json as (__ \ 'newPassword).read[Password]
  def newPassword_= (v: Password) = setValue((__ \ 'newPassword), Json.toJson(v))

  def copy(passwordChange: PasswordChange): PasswordChange = new PasswordChange(this.json.as[JsObject] ++ passwordChange.json.as[JsObject])
  def copy(json: JsValue): PasswordChange = PasswordChange(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(passwordChange, _) => passwordChange
    case JsError(_) => new PasswordChange(this.json)
  }
}

/**
  * Factory class for creating [[PasswordChange]] instances.
  */
object PasswordChange {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[PasswordChange]] class with the specified JSON.
    *
    * @param json The password change data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[PasswordChange] = {
    validatePasswordChange.reads(json).fold(
      valid = { validated => JsSuccess(new PasswordChange(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[PasswordChange]] class with the specified values.
    *
    * @param currentPassword  The current password.
    * @param newPassword      The new password to set.
    * @return                 A new instance of the [[PasswordChange]] class.
    */
  def apply(
    currentPassword: Password,
    newPassword: Password
  ): PasswordChange = new PasswordChange(passwordChangeWrites.writes(
    currentPassword,
    newPassword
  ))

  /**
    * Extracts the content of the specified [[PasswordChange]].
    *
    * @param passwordChange The [[PasswordChange]] to extract the content from.
    * @return               An `Option` that contains the extracted data,
    *                       or `None` if `passwordChange` is `null`.
    */
  def unapply(passwordChange: PasswordChange) = {
    if (passwordChange eq null) None
    else Some((
      passwordChange.currentPassword,
      passwordChange.newPassword
    ))
  }

  /**
    * Serializes/Deserializes a [[PasswordChange]] to/from JSON.
    */
  implicit val passwordChangeFormat: Format[PasswordChange] = new Format[PasswordChange] {
    def reads(json: JsValue) = PasswordChange(json)
    def writes(passwordChange: PasswordChange) = passwordChange.json
  }

  /**
    * Serializes a [[PasswordChange]] to JSON.
    * @note Used internally by `apply`.
    */
  private val passwordChangeWrites = (
    (__ \ 'currentPassword).write[Password] ~
    (__ \ 'newPassword).write[Password]
  ).tupled

  /**
    * Validates the JSON representation of the specified password.
    */
  private def password(key: Symbol) = (__ \ key).json.pick[JsValue] andThen validatePassword

  /**
    * Validates the JSON representation of a [[PasswordChange]].
    *
    * @return A `Reads` that validates the JSON representation of a [[PasswordChange]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validatePasswordChange = (
    ((__ \ 'currentPassword).json.copyFrom(password('currentPassword))) ~
    ((__ \ 'newPassword).json.copyFrom(password('newPassword)))
  ).reduce
}
