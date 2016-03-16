/*#
  * @file User.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._
import org.joda.time.{DateTime, LocalDate}
import models.common.api.{Address, HistoryEvent, State}

/**
  * Defines the `User` schema for Swagger.
  */
@ApiModel(value = "User", description = "Represents a user")
trait User {

  @ApiModelProperty(value = "The identifier of the user", dataType = "string", /* readOnly = true, */ position = 1)
  def id: Option[String]
  @ApiModelProperty(value = "The email address of the user", dataType = "string", required = true, position = 2)
  def email: Option[String]
  @ApiModelProperty(value = "The username of the user", dataType = "string", required = true, position = 3)
  def username: Option[String]
  @ApiModelProperty(value = "The password of the user", dataType = "Password", required = true, position = 4)
  def password: Option[Password]
  @ApiModelProperty(value = "The mobile number of the user", dataType = "string", position = 5)
  def mobile: Option[String]
  @ApiModelProperty(value = "The first name of the user", dataType = "string", required = true, position = 6)
  def firstName: Option[String]
  @ApiModelProperty(value = "The last name of the user", dataType = "string", required = true, position = 7)
  def lastName: Option[String]
  @ApiModelProperty(value = "The birth date of the user in ISO 8601 format", dataType = "localdate", required = true, position = 8)
  def birthDate: Option[LocalDate]
  @ApiModelProperty(value = "The language of the user in ISO 639-2 format, optionally followed by an ISO 3166-1 alpha-2 country code", dataType = "string", position = 9)
  def lang: Option[String]
  @ApiModelProperty(value = "The biography of the user", dataType = "string", position = 10)
  def biography: Option[String]
  @ApiModelProperty(value = "The name of the company the user works for", dataType = "string", position = 11)
  def company: Option[String]
  @ApiModelProperty(value = "The website of the user", dataType = "string", position = 12)
  def website: Option[String]
  @ApiModelProperty(value = "The current state of the user", dataType = "string", /* readOnly = true, */ position = 13)
  def state: Option[State]
  @ApiModelProperty(value = "The addresses of the user", dataType = "List[Address]", position = 14)
  def addresses: Option[List[Address]]
  @ApiModelProperty(value = "Whether the user is subscribed to the newsletter", dataType = "boolean", /* readOnly = true, */ position = 15)
  def newsletter: Boolean
  @ApiModelProperty(value = "Whether this is user is public", dataType = "boolean", /* readOnly = true, */ position = 16)
  def public: Boolean
  @ApiModelProperty(value = "The history of the user", dataType = "List[HistoryEvent]", /* readOnly = true, */ position = 17)
  def history: Option[List[HistoryEvent]]
  @ApiModelProperty(value = "The time the user was created in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 18)
  def creationTime: Option[DateTime]
}
