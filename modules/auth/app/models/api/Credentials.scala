/*#
  * @file Credentials.scala
  * @begin 16-Apr-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Credentials` schema for Swagger.
  */
@ApiModel(value = "Credentials", description = "The piece of information that verifies the identity of a subject")  
trait Credentials {

  @ApiModelProperty(value = "The subject represented by an account", dataType = "string", required = true, position = 1)
  def principal: String
  @ApiModelProperty(value = "The secret that grants access to the account", dataType = "string", required = true, position = 2)
  def secret: String
}
