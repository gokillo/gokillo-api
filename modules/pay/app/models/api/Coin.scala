/*#
  * @file Coin.scala
  * @begin 5-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Coin` schema for Swagger.
  */
@ApiModel(value = "Coin", description = "Represents a monetary value")
trait Coin {

  @ApiModelProperty(value = "The number of monetary units", dataType = "double", required = true, position = 1)
  def value: Double
  @ApiModelProperty(value = "The currency of the monetary value", dataType = "string", required = true, position = 2)
  def currency: String
  @ApiModelProperty(value = "The reference currency", dataType = "string", position = 3)
  def refCurrency: Option[String]
  @ApiModelProperty(value = "The exchange rate to convert the monetary value to reference currency", dataType = "double", position = 4)
  def rate: Option[Double]
}
