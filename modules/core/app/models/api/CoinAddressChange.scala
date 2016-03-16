/*#
  * @file CoinAddressChange.scala
  * @begin 28-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `CoinAddressChange` schema for Swagger.
  */
@ApiModel(value = "CoinAddressChange", description = "Represents a coin address change")
trait CoinAddressChange {

  @ApiModelProperty(value = "The current coin address", dataType = "string", required = true, position = 1)
  def currentCoinAddress: String
  @ApiModelProperty(value = "The new coin address to set", dataType = "string", required = true, position = 2)
  def newCoinAddress: String
}
