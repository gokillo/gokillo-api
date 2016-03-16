/*#
  * @file Reward.scala
  * @begin 12-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import com.wordnik.swagger.annotations._
import org.joda.time.LocalDate

/**
  * Defines the `Reward` schema for Swagger.
  */
@ApiModel(value = "Reward", description = "Represents a reward granted to a backer")
trait Reward {

  @ApiModelProperty(value = "The identifier of the reward", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The amount that entitles a backer to receive this reward", dataType = "double", required = true, position = 2)
  def pledgeAmount: Option[Double]
  @ApiModelProperty(value = "The description of the reward", dataType = "string", required = true, position = 3)
  def description: Option[String]
  @ApiModelProperty(value = "The estimated delivery date of the reward in ISO 8601 format", dataType = "localdate", required = true, position = 4)
  def estimatedDeliveryDate: Option[LocalDate]
  @ApiModelProperty(value = "How the reward will be delivered", dataType = "string", required = true, position = 5)
  def shipping: Option[String]
  @ApiModelProperty(value = "The number of times the reward has been selected", dataType = "int", position = 6)
  def selectCount: Option[Int]
  @ApiModelProperty(value = "The number of reward instances still available", dataType = "int", position = 7)
  def availableCount: Option[Int]
}
