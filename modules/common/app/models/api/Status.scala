/*#
  * @file Status.scala
  * @begin 16-Aug-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._

/**
  * Defines the `Status` schema for Swagger.
  */
@ApiModel(value = "Status", description = "Represents the status of an entity")
trait Status {
  @ApiModelProperty(value = "The value of the status", dataType = "string", /* readOnly = true, */ position = 1)
  def value: String
  @ApiModelProperty(value = "The time the status was set in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 2)
  def timestamp: DateTime
}
