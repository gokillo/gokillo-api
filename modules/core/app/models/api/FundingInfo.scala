/*#
  * @file FundingInfo.scala
  * @begin 15-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._

/**
  * Defines the `FundingInfo` schema for Swagger.
  */
@ApiModel(value = "FundingInfo", description = "Provides information about the funding of a project")
trait FundingInfo {

  @ApiModelProperty(value = "The coin address where to send funds in case of success", dataType = "string", required = true, position = 1)
  def coinAddress: Option[String]
  @ApiModelProperty(value = "The target amount", dataType = "double", required = true, position = 2)
  def targetAmount: Option[Double]
  @ApiModelProperty(value = "The amount raised so far", dataType = "double", /* readOnly = true, */ position = 3)
  def raisedAmount: Option[Double]
  @ApiModelProperty(value = "The amount currency", dataType = "string", required = true, position = 4)
  def currency: Option[String]
  @ApiModelProperty(value = "The number of pledges", dataType = "int", /* readOnly = true, */ position = 5)
  def pledgeCount: Option[Int]
  @ApiModelProperty(value = "The number of days the fundraising campaign lasts", dataType = "int", required = true, position = 6)
  def duration: Option[Int]
  @ApiModelProperty(value = "The start time of the fundraising campaign in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 7)
  def startTime: Option[DateTime]
  @ApiModelProperty(value = "The end time of the fundraising campaign in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 8)
  def endTime: Option[DateTime]
}
