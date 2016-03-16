/*#
  * @file Pledge.scala
  * @begin 18-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._
import models.common.api.State
import models.pay.api.Coin

/**
  * Defines the `Pledge` schema for Swagger.
  */
@ApiModel(value = "Pledge", description = "Represents a pledge to a project")
trait Pledge {

  @ApiModelProperty(value = "The identifier of the pledge", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the project to fund", dataType = "string", /* readOnly = true, */ position = 2)
  def projectId: Option[String]
  @ApiModelProperty(value = "The identifier of the reward the pledge is associated with", dataType = "string", /* readOnly = true, */ position = 3)
  def rewardId: Option[String]
  @ApiModelProperty(value = "Information about the backer that pledged to the project", dataType = "BackerInfo", /* readOnly = true, */ position = 4)
  def backerInfo: Option[BackerInfo]
  @ApiModelProperty(value = "The amount of the pledge", dataType = "Coin", /* readOnly = true, */ position = 5)
  def amount: Option[Coin]
  @ApiModelProperty(value = "The current state of the pledge", dataType = "State", /* readOnly = true, */ position = 6)
  def state: Option[State]
  @ApiModelProperty(value = "The time the pledge was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 7)
  def creationTime: Option[DateTime]
}
