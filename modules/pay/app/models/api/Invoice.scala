/*#
  * @file Invoice.scala
  * @begin 11-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import org.joda.time.DateTime
import com.wordnik.swagger.annotations._

/**
  * Defines the `Invoice` schema for Swagger.
  */
@ApiModel(value = "Invoice", description = "Represents an invoice to request a payment in cryptocurrency")
trait Invoice {

  @ApiModelProperty(value = "The identifier of the order this invoice is associated with", dataType = "string", /* readOnly = true, */ position = 1)
  def orderId: String
  @ApiModelProperty(value = "The receiving coin address", dataType = "string", /* readOnly = true, */ position = 2)
  def coinAddress: String
  @ApiModelProperty(value = "The amount to be paid", dataType = "Coin", /* readOnly = true, */ position = 3)
  def amount: Coin
  @ApiModelProperty(value = "The label associated with the coin address", dataType = "string", /*readOnly = true, */ position = 4)
  def label: Option[String]
  @ApiModelProperty(value = "The message that describes the transaction", dataType = "string", /*readOnly = true, */ position = 5)
  def message: Option[String]
  @ApiModelProperty(value = "The invoice QR-Code", dataType = "string", /* readOnly = true, */ position = 6)
  def qrCode: String
  @ApiModelProperty(value = "The time the invoice expires", dataType = "DateTime", /* readOnly = true, */ position = 7)
  def expirationTime: DateTime
}
