/*#
  * @file Project.scala
  * @begin 12-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._
import models.common.api.{HistoryEvent, State}
import models.auth.IdentityMode._

/**
  * Defines the `Project` schema for Swagger.
  */
@ApiModel(value = "Project", description = "Represents a project")
trait Project {

  @ApiModelProperty(value = "The identifier of the project", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the account that owns the project", dataType = "string", /* readOnly = true, */ position = 2)
  def accountId: Option[String]
  @ApiModelProperty(value = "The identity mode of the project owner; valid values are username, fullName, or companyName", dataType = "IdentityMode", required = true, position = 3)
  def ownerIdentityMode: Option[IdentityMode]
  @ApiModelProperty(value = "The name of the project", dataType = "string", required = true, position = 4)
  def name: Option[String]
  @ApiModelProperty(value = "The categories the project belongs to", dataType = "List[string]", required = true, position = 5)
  def categories: Option[List[String]]
  @ApiModelProperty(value = "The short blurb of the project", dataType = "string", required = true, position = 6)
  def blurb: Option[String]
  @ApiModelProperty(value = "The description of the project", dataType = "string", required = true, position = 7)
  def description: Option[String]
  @ApiModelProperty(value = "The location of the project", dataType = "string", required = true, position = 8)
  def location: Option[String]
  @ApiModelProperty(value = "Whether the project was picked", dataType = "boolean", /* readOlny = true, */ position = 9)
  def picked: Boolean
  @ApiModelProperty(value = "The current state of the project", dataType = "string", /* readOnly = true, */ position = 10)
  def state: Option[State]
  @ApiModelProperty(value = "The funding information of the project", dataType = "FundingInfo", required = true, position = 11)
  def fundingInfo: Option[FundingInfo]
  @ApiModelProperty(value = "The rewarding plan of the project", dataType = "List[Reward]", position = 12)
  def rewards: Option[List[Reward]]
  @ApiModelProperty(value = "The frequently asked questions about the project", dataType = "List[Faq]", position = 13)
  def faqs: Option[List[Faq]]
  @ApiModelProperty(value = "The history of the project", dataType = "List[HistoryEvent]", /* readOnly = true, */ position = 14)
  def history: Option[List[HistoryEvent]]
  @ApiModelProperty(value = "The time the project was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 15)
  def creationTime: Option[DateTime]
}
