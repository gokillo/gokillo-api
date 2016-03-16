/*#
  * @file Notification.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `Notification` schema for Swagger.
  */
@ApiModel(value = "Notification", description = "Represents a notification sent to a group of recipients")
trait Notification {

  @ApiModelProperty(value = "The identifier of the notification", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The time the notification was sent in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 2)
  def sentTime: Option[DateTime]
  @ApiModelProperty(value = "The recipients to send the notification to", dataType = "List[string]", required = true, position = 3)
  def recipients: Option[List[String]]
  @ApiModelProperty(value = "The subject of the notification", dataType = "string", required = true, position = 4)
  def subject: Option[String]
  @ApiModelProperty(value = "The body of the notificaton", dataType = "string", required = true, position = 5)
  def body: Option[String]
  @ApiModelProperty(value = "The time the notification was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 6)
  def creationTime: Option[DateTime]
}
