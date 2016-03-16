/*#
  * @file Transaction.scala
  * @begin 28-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime
import models.pay.TransactionType._

/**
  * Defines the `Transaction` schema for Swagger.
  */
@ApiModel(value = "Transaction", description = "Represents a pay transaction")
trait Transaction {

  @ApiModelProperty(value = "The identifier of the transaction", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the order that generated the transaction", dataType = "string", /* readOnly = true, */ position = 2)
  def orderId: Option[String]
  @ApiModelProperty(value = "The transaction type", dataType = "string", /* readOnly = true, */ position = 3)
  def transactionType: TransactionType
  @ApiModelProperty(value = "The transaction hash", dataType = "string", /* readOnly = true, */ position = 4)
  def hash: Option[String]
  @ApiModelProperty(value = "The recipient coin address", dataType = "string", /* readOnly = true, */ position = 5)
  def coinAddress: Option[String]
  @ApiModelProperty(value = "The amount of the transaction", dataType = "Coin", /* readOnly = true, */ position = 6)
  def amount: Coin
  @ApiModelProperty(value = "The transaction fee", dataType = "Coin", /* readOnly = true, */ position = 7)
  def fee: Option[Coin]
  @ApiModelProperty(value = "The time the transaction was created, in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 9)
  def creationTime: Option[DateTime]
}
