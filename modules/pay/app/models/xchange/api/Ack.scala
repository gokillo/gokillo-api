/*#
  * @file Ack.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Ack` schema for Swagger.
  */
@ApiModel(value = "Ack", description = "Represents a transaction ack received from the exchange service")
trait Ack {

  @ApiModelProperty(value = "The request hash", dataType = "string", /* readOnly = true, */ position = 1)
  def hash: String
  @ApiModelProperty(value = "The transaction type", dataType = "string", /* readOnly = true, */ position = 2)
  def transactionType: String
  @ApiModelProperty(value = "The recipient coin address", dataType = "string", /* readOnly = true, */ position = 3)
  def address: String
  @ApiModelProperty(value = "The transaction amount", dataType = "double", /* readOnly = true, */ position = 4)
  def amount: Double
  @ApiModelProperty(value = "The transaction currency", dataType = "string", /* readOnly = true, */ position = 5)
  def currency: String
}
