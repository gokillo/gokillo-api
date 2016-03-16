/*#
  * @file Thread.scala
  * @begin 14-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime
import models.common.api.RefId

/**
  * Defines the `Thread` schema for Swagger.
  */
@ApiModel(value = "Thread", description = "Represents a message thread")
trait Thread {

  @ApiModelProperty(value = "The identifier of the thread", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "A reference to the object associated with the thread", dataType = "RefId", required = true, position = 2)
  def refId: Option[RefId]
  @ApiModelProperty(value = "The subject of the thread", dataType = "string", required = true, position = 3)
  def subject: Option[String]
  @ApiModelProperty(value = "The username of the user that created the thread", dataType = "string", /* readOnly = true, */ position = 4)
  def createdBy: Option[String]
  @ApiModelProperty(value = "Whether the thread is confidential", dataType = "boolean", position = 5)
  def confidential: Boolean
  @ApiModelProperty(value = "The username of the users allowed to participate in the thread", dataType = "List[string]", /* readOnly = true, */ position = 6)
  def grantees: Option[List[String]]
  @ApiModelProperty(value = "The number of messages in the thraed", dataType = "int", /* readOnly = true, */ position = 7)
  def messageCount: Option[Int]
  @ApiModelProperty(value = "The time the last activity happened on the thread in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 8)
  def lastActivityTime: Option[DateTime]
  @ApiModelProperty(value = "The time the thread was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 9)
  def creationTime: Option[DateTime]
}
