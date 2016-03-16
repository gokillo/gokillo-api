/*#
  * @file Account.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `Account` schema for Swagger.
  */
@ApiModel(value = "Account", description = "Represents a user account")
trait Account {

  @ApiModelProperty(value = "The identifier of the account", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The name of the account", dataType = "string", position = 2)
  def name: Option[String]
  @ApiModelProperty(value = "The time the account was activated in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 3)
  def activationTime: Option[DateTime]
  @ApiModelProperty(value = "Whether this is the default account", dataType = "boolean", position = 4)
  def default: Boolean
}
