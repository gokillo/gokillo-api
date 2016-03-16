/*#
  * @file WalletTransaction.scala
  * @begin 13-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `WalletTransaction` schema for Swagger.
  */
@ApiModel(value = "WalletTransaction", description = "Provides information about a wallet transaction")
trait WalletTransaction {

  @ApiModelProperty(value = "The sum of inputs that are spending coins in the wallet", dataType = "double", /* readOnly = true, */ position = 1)
  def sentFrom: Double
  @ApiModelProperty(value = "The sum of outputs that are sending coins to the wallet", dataType = "double", /* readOnly = true, */ position = 2)
  def sentTo: Double
  @ApiModelProperty(value = "The difference between sentFrom and sentTo", dataType = "double", /* readOnly = true, */ position = 3)
  def total: Double
}
