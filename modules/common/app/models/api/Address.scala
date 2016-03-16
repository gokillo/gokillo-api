/*#
  * @file Address.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Address` schema for Swagger.
  */
@ApiModel(value = "Address", description = "Represents a user address")
trait Address {

  @ApiModelProperty(value = "The address name", dataType = "string", required = true, position = 1)
  def name: Option[String]
  @ApiModelProperty(value = "Who takes care of correspondence", dataType = "string", position = 2)
  def careOf: Option[String]
  @ApiModelProperty(value = "The street address", dataType = "string", position = 3)
  def street: Option[String]
  @ApiModelProperty(value = "The house number", dataType = "string", position = 4)
  def houseNr: Option[String]
  @ApiModelProperty(value = "The zip code", dataType = "string", required = true, position = 5)
  def zip: Option[String]
  @ApiModelProperty(value = "The city address", dataType = "string", required = true, position = 6)
  def city: Option[String]
  @ApiModelProperty(value = "The state address", dataType = "string", position = 7)
  def state: Option[String]
  @ApiModelProperty(value = "The country address", dataType = "string", required = true, position = 8)
  def country: Option[String]
  @ApiModelProperty(value = "The time zone offset in +/-HH:MM format", dataType = "string", required = true, position = 8)
  def timeZone: Option[String]
  @ApiModelProperty(value = "The primary phone number", dataType = "string", position = 9)
  def phone: Option[String]
  @ApiModelProperty(value = "Whether this is the default address", dataType = "boolean", position = 10)
  def default: Boolean
}
