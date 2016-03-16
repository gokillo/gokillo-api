/*#
  * @file ApiConsumer.scala
  * @begin 25-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `ApiConsumer` schema for Swagger.
  */
@ApiModel(value = "ApiConsumer", description = "Represents any client system that uses the application API")
trait ApiConsumer {

  @ApiModelProperty(value = "The identifier of the API consumer", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the account associated with the API consumer", dataType = "string", /* readOnly = true, */ position = 2)
  def accountId: Option[String]
  @ApiModelProperty(value = "The identifier of the account that owns the API consumer", dataType = "string", /* readOnly = true, */ position = 3)
  def ownerId: Option[String]
  @ApiModelProperty(value = "The name of the API consumer", dataType = "string", required = true, position = 4)
  def name: Option[String]
  @ApiModelProperty(value = "The description of the API consumer", dataType = "string", required = true, position = 5)
  def description: Option[String]
  @ApiModelProperty(value = "The website of API consumer", dataType = "string", position = 6)
  def website: Option[String]
  @ApiModelProperty(value = "The secret API key", dataType = "string", position = 7)
  def apiKey: Option[String]
  @ApiModelProperty(value = "Whether this is API consumer is native", dataType = "boolean", /* readOnly = true, */ position = 8)
  def native: Boolean
  @ApiModelProperty(value = "The time the API consumer was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 9)
  def creationTime: Option[DateTime]
}
