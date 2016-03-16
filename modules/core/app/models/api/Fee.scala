/*#
  * @file Fee.scala
  * @begin 30-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._
import models.pay.api.Coin

/**
  * Defines the `Fee` schema for Swagger.
  */
@ApiModel(value = "Fee", description = "Information about the fee withheld from the amount raised by a project on success")
trait Fee {

  @ApiModelProperty(value = "The identifier of the fee", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the project the fee applied to", dataType = "string", /* readOnly = true, */ position = 2)
  def projectId: String
  @ApiModelProperty(value = "The amount of the fee", dataType = "Coin", /* readOnly = true, */ position = 3)
  def amount: Coin
  @ApiModelProperty(value = "The amount of the value-added tax", dataType = "Coin", /* readOnly = true, */ position = 4)
  def vat: Option[Coin]
  @ApiModelProperty(value = "The time the fee was withheld in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 5)
  def withdrawalTime: Option[DateTime]
  @ApiModelProperty(value = "The time the fee was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 6)
  def creationTime: Option[DateTime]
}
