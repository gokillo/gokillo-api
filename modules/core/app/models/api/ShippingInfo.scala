/*#
  * @file ShippingInfo.scala
  * @begin 22-Dec-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import com.wordnik.swagger.annotations._
import models.common.api.Address

/**
  * Defines the `ShippingInfo` schema for Swagger.
  */
@ApiModel(value = "ShippingInfo", description = "Provides shipping info for the delivery of physical rewards")
trait ShippingInfo {

  @ApiModelProperty(value = "The address where physical rewards are received", dataType = "Address", required = true, position = 1)
  def address: Address
  @ApiModelProperty(value = "Any notice regarding the delivery", dataType = "string", position = 2)
  def notice: Option[String]
}
