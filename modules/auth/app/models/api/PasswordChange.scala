/*#
  * @file PasswordChange.scala
  * @begin 14-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `PasswordChange` schema for Swagger.
  */
@ApiModel(value = "PasswordChange", description = "Represents a password change")
trait PasswordChange {

  @ApiModelProperty(value = "The current password of the user", dataType = "Password", required = true, position = 1)
  def currentPassword: Password
  @ApiModelProperty(value = "The new password to set", dataType = "Password", required = true, position = 2)
  def newPassword: Password
}
