/*#
  * @file Leftover.scala
  * @begin 6-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._
import models.pay.api.Coin

/**
  * Defines the `Leftover` schema for Swagger.
  */
@ApiModel(value = "Leftover", description = "Information about monetary value left due to roundings or exchange variations") 
trait Leftover {

  @ApiModelProperty(value = "The identifier of the leftover", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The amount of the leftover", dataType = "Coin", /* readOnly = true, */ position = 2)
  def amount: Coin
  @ApiModelProperty(value = "The number of times the amount has been increased", dataType = "int", /* readOnly = true, */ position = 3)
  def count: Int
  @ApiModelProperty(value = "The time the leftover was withheld in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 4)
  def withdrawalTime: Option[DateTime]
  @ApiModelProperty(value = "The time the leftover was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 5)
  def creationTime: Option[DateTime]
}
