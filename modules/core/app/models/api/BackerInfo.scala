/*#
  * @file BackerInfo.scala
  * @begin 26-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import com.wordnik.swagger.annotations._
import models.common.api.Address
import models.auth.IdentityMode._

/**
  * Defines the `BackerInfo` schema for Swagger.
  */
@ApiModel(value = "BackerInfo", description = "Provides information about a backer that pledged to a project")
trait BackerInfo {

  @ApiModelProperty(value = "The identifier of the backer account", dataType = "string", /* readOnly = true, */ position = 1)
  def accountId: String
  @ApiModelProperty(value = "The identity mode of the backer", dataType = "IdentityMode", /* readOnly = true, */ position = 2)
  def identityMode: IdentityMode
  @ApiModelProperty(value = "The coin addresses funds came from", dataType = "List[string]", /* readOnly = true, */ position = 3)
  def fundingCoinAddresses: Option[List[String]]
  @ApiModelProperty(value = "The coin address where to send funds back in case of failure", dataType = "string", /* readOnly = true, */ position = 4)
  def refundCoinAddress: Option[String]
  @ApiModelProperty(value = "The address where the delivery of physical rewards will be received", dataType = "Address", /* readOnly = true, */ position = 5)
  def shippingAddress: Option[Address]
  @ApiModelProperty(value = "Backer's notice about a reward or refund, if any", dataType = "string", /* readOnly = true, */ position = 6)
  def notice: Option[String]
}
