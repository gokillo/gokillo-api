/*#
  * @file Faq.scala
  * @begin 12-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Faq` schema for Swagger.
  */
@ApiModel(value = "Faq", description = "Represents a frequently asked question")
trait Faq {

  @ApiModelProperty(value = "The frequently asked question", dataType = "string", required = true, position = 1)
  def question: Option[String]
  @ApiModelProperty(value = "The answer to the frequently asked question", dataType = "string", required = true, position = 2)
  def answer: Option[String]
}
