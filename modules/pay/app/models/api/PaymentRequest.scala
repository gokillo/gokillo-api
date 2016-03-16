/*#
  * @file PaymentRequest.scala
  * @begin 15-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime
import models.common.api.RefId
import models.auth.IdentityMode._

/**
  * Defines the `PaymentRequest` schema for Swagger.
  */
@ApiModel(value = "PaymentRequest", description = "Represents a payment request in fiat money")
trait PaymentRequest {

  @ApiModelProperty(value = "A reference to the object associated with the payment request", dataType = "RefId", required = true,  position = 1)
  def refId: RefId
  @ApiModelProperty(value = "The amount to get paid", dataType = "Coin", required = true, position = 2)
  def amount: Coin
  @ApiModelProperty(value = "The identifier of the account that receives the payment request", dataType = "string", /*readOnly = true, */ position = 3)
  def accountId: Option[String]
  @ApiModelProperty(value = "The identifier of the order associated with the payment request", dataType = "string", position = 4)
  def orderId: Option[String]
  @ApiModelProperty(value = "The identity mode of the payment request issuer; valid values are anonym or username", dataType = "IdentityMode", position = 5)
  def issuerIdentityMode: Option[IdentityMode]
  @ApiModelProperty(value = "The label associated with the payment request", dataType = "string", position = 6)
  def label: Option[String]
  @ApiModelProperty(value = "The message that describes the payment request", dataType = "string", position = 7)
  def message: Option[String]
}
