/*#
  * @file HistoryEvent.scala
  * @begin 3-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `HistoryEvent` schema for Swagger.
  */
@ApiModel(value = "HistoryEvent", description = "Represents a history event")
trait HistoryEvent {

  @ApiModelProperty(value = "The event text", dataType = "string", required = true, position = 1)
  def text: String
  @ApiModelProperty(value = "The event detail", dataType = "string", position = 2)
  def detail: Option[String]
  @ApiModelProperty(value = "The time the event occurred in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 3)
  def timestamp: DateTime
}
