/*#
  * @file RefId.scala
  * @begin 1-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `RefId` schema for Swagger.
  */
@ApiModel(value = "RefId", description = "Represents a reference to an object id")
trait RefId {
  @ApiModelProperty(value = "The domain of the object identified by the referenced id", dataType = "string", required = true, position = 1)
  def domain: String
  @ApiModelProperty(value = "The name of the referenced id", dataType = "string", required = true, position = 2)
  def name: String
  @ApiModelProperty(value = "The value of the referenced id", dataType = "string", required = true, position = 3)
  def value: String
  @ApiModelProperty(value = "The values of the referenced id", dataType = "Seq[string]", required = true, position = 4)
  def mvalue: Seq[String]
}
