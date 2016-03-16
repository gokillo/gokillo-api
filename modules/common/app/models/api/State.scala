/*#
  * @file State.scala
  * @begin 25-Nov-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._

/**
  * Defines the `State` schema for Swagger.
  */
@ApiModel(value = "State", description = "Represents the state of a FSM")
trait State {
  @ApiModelProperty(value = "The value of the state", dataType = "string", /* readOnly = true, */ position = 1)
  def value: String
  @ApiModelProperty(value = "The time the state was set in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 2)
  def timestamp: DateTime
}
