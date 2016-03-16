/*#
  * @file Notification.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsEntity

/**
  * Represents a notification sent to a group of recipients.
  *
  * @constructor  Initializes a new instance of the [[Notification]] class.
  * @param json   The notification data as JSON.
  */
class Notification protected(protected var json: JsValue) extends JsEntity with api.Notification {

  def sentTime = json as (__ \ 'sentTime).readNullable[DateTime]
  def sentTime_= (v: Option[DateTime]) = setValue((__ \ 'sentTime), Json.toJson(v))
  def recipients = json as (__ \ 'recipients).readNullable[List[String]]
  def recipients_= (v: Option[List[String]]) = setValue((__ \ 'recipients), Json.toJson(v))
  def subject = json as (__ \ 'subject).readNullable[String]
  def subject_= (v: Option[String]) = setValue((__ \ 'subject), Json.toJson(v))
  def body = json as (__ \ 'body).readNullable[String]
  def body_= (v: Option[String]) = setValue((__ \ 'body), Json.toJson(v))

  def copy(notification: Notification): Notification = new Notification(this.json.as[JsObject] ++ notification.json.as[JsObject])
  def copy(json: JsValue): Notification = Notification(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(notification, _) => notification
    case JsError(_) => new Notification(this.json)
  }
}

/**
  * Factory class for creating [[Notification]] instances.
  */
object Notification extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Notification]] class with the specified JSON.
    *
    * @param json     The notification data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Notification] = {
    { relaxed match {
      case Some(r) => validateNotification(r)
      case _ => validateNotification
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new Notification(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Notification]] class with the specified values.
    *
    * @param id           The identifier of the notification.
    * @param sentTime     The time the notification was sent.
    * @param recipients   The recipients to send the notification to.
    * @param subject      The subject of the notification.
    * @param body         The body of the notification.
    * @param creationTime The time the notification was created.
    * @return             A new instance of the [[Notification]] class.
    */
  def apply(
    id: Option[String] = None,
    sentTime: Option[DateTime] = None,
    recipients: Option[List[String]] = None,
    subject: Option[String] = None,
    body: Option[String] = None,
    creationTime: Option[DateTime] = None
  ): Notification = new Notification(
    notificationWrites.writes(
      id,
      sentTime,
      recipients,
      subject,
      body,
      creationTime
    )
  ) 

  /**
    * Extracts the content of the specified [[Notification]].
    *
    * @param notification The [[Notification]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `notification` is `null`.
    */
  def unapply(notification: Notification) = {
    if (notification eq null) None
    else Some((
      notification.id,
      notification.sentTime,
      notification.recipients,
      notification.subject,
      notification.body,
      notification.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Notification]] to/from JSON.
    */
  implicit val notificationFormat: Format[Notification] = notificationFormat(None)
  def notificationFormat(relaxed: Option[Boolean]) = new Format[Notification] {
    def reads(json: JsValue) = Notification(json, relaxed)
    def writes(notification: Notification) = notification.json
  }

  /**
    * Serializes a [[Notification]] to JSON.
    * @note Used internally by `apply`.
    */
  private val notificationWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'sentTime).writeNullable[DateTime] ~
    (__ \ 'recipients).writeNullable[List[String]] ~
    (__ \ 'subject).writeNullable[String] ~
    (__ \ 'body).writeNullable[String] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled
  
  /**
    * Validates the JSON representation of the recipient list.
    */
  private val validateRecipients = verifyingIf((arr: JsArray) => arr.value.nonEmpty)(Reads.list(emailAddressOrWildcard))
  private val recipients: Reads[JsArray] = (__ \ 'recipients).json.pick[JsArray] andThen validateRecipients

  /**
    * Validates the JSON representation of a [[Notification]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Notification]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateNotification = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'sentTime).json.pickBranch orEmpty) ~
    ((__ \ 'recipients).json.copyFrom(recipients) orEmpty) ~
    ((__ \ 'subject).json.pickBranch orEmpty) ~
    ((__ \ 'body).json.pickBranch orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[Notification]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Notification]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateNotification(relaxed: Boolean) = (
    ((__ \ 'recipients).json.copyFrom(recipients) orEmptyIf relaxed) ~
    ((__ \ 'subject).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'body).json.pickBranch orEmptyIf relaxed)
  ).reduce
}
