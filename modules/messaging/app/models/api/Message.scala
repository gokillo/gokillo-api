/*#
  * @file Message.scala
  * @begin 11-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `Message` schema for Swagger.
  */
@ApiModel(value = "Message", description = "Represents a message sent by a user")
trait Message {

  @ApiModelProperty(value = "The identifier of the message", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The identifier of the thread the message belongs to", dataType = "string", /* readOnly = true, */ position = 2)
  def threadId: Option[String]
  @ApiModelProperty(value = "The username of the user that created the message", dataType = "string", /* readOnly = true, */ position = 3)
  def createdBy: Option[String]
  @ApiModelProperty(value = "The body of the message", dataType = "string", required = true, position = 4)
  def body: Option[String]
  @ApiModelProperty(value = "The sequence number of the message in the thread", dataType = "int", /* readOnly = true, */ position = 5)
  def seqNumber: Option[Int]
  @ApiModelProperty(value = "The time the message was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 6)
  def creationTime: Option[DateTime]
}
