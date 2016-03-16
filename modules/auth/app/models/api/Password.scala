/*#
  * @file Password.scala
  * @begin 14-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Password` schema for Swagger.
  */
@ApiModel(value = "Password", description = "Represents a password")
trait Password {

  @ApiModelProperty(value = "The password in either plaintext or hashtext", dataType = "string", required = true, position = 1)
  def value: String
  @ApiModelProperty(value = "The salt to be used to hash the password", dataType = "string", position = 2)
  def salt: Option[String]
}
