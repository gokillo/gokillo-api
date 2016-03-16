/*#
  * @file WalletInfo.scala
  * @begin 9-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import com.wordnik.swagger.annotations._
import models.pay.CoinNet._

/**
  * Defines the `WalletInfo` schema for Swagger.
  */
@ApiModel(value = "WalletInfo", description = "Provides information about a wallet")
trait WalletInfo {

  @ApiModelProperty(value = "The name of the wallet on the file system", dataType = "string", /* readOnly = true, */ position = 1)
  def filename: String
  @ApiModelProperty(value = "The description of the wallet", dataType = "string", /* readOnly = true, */ position = 2)
  def description: Option[String]
  @ApiModelProperty(value = "The current receive address of the wallet", dataType = "string", /* readOnly = true, */ position = 3)
  def receiveAddress: String
  @ApiModelProperty(value = "The balance calculated assuming all pending transactions", dataType = "double", /* readOnly = true, */ position = 4)
  def balance: Double
  @ApiModelProperty(value = "The balance that could be safely used to create new spends", dataType = "double", /* readOnly = true, */ position = 5)
  def spendable: Double
  @ApiModelProperty(value = "The coin network on which the wallet operates", dataType = "string", /* readOnly = true, */ position = 6)
  def net: CoinNet
  @ApiModelProperty(value = "The version of the wallet", dataType = "int", /* readOnly = true, */ position = 7)
  def ver: Int
  @ApiModelProperty(value = "Whether the wallet is encrypted", dataType = "boolean", /* readOnly = true, */ position = 8)
  def encrypted: Boolean
  @ApiModelProperty(value = "The pending transactions associated with the wallet", dataType = "List[WalletTransaction]", /* readOnly = true, */ position = 9)
  def pendingTransactions: Option[List[WalletTransaction]]
}
