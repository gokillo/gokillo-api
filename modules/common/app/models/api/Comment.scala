/*#
  * @file Comment.scala
  * @begin 4-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Comment` schema for Swagger.
  */
@ApiModel(value = "Comment", description = "Represents a user comment about an action")
trait Comment {

  @ApiModelProperty(value = "The comment text", dataType = "string", required = true, position = 1)
  def text: String
}
