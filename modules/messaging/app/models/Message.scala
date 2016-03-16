/*#
  * @file Message.scala
  * @begin 11-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsEntity

/**
  * Represents a message sent by a user.
  *
  * @constructor  Initializes a new instance of the [[Message]] class.
  * @param json   The message data as JSON.
  */
class Message protected(protected var json: JsValue) extends JsEntity with api.Message {

  def threadId = json as (__ \ 'threadId).readNullable[String]
  def threadId_= (v: Option[String]) = setValue((__ \ 'threadId), Json.toJson(v))
  def createdBy = json as (__ \ 'createdBy).readNullable[String]
  def createdBy_= (v: Option[String]) = setValue((__ \ 'createdBy), Json.toJson(v))
  def body = json as (__ \ 'body).readNullable[String]
  def body_= (v: Option[String]) = setValue((__ \ 'body), Json.toJson(v))
  def seqNumber = json as (__ \ 'seqNumber).readNullable[Int]
  def seqNumber_= (v: Option[Int]) = setValue((__ \ 'seqNumber), Json.toJson(v))

  def copy(message: Message): Message = new Message(this.json.as[JsObject] ++ message.json.as[JsObject])
  def copy(json: JsValue): Message = Message(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(message, _) => message
    case JsError(_) => new Message(this.json)
  }
}

/**
  * Factory class for creating [[Message]] instances.
  */
object Message extends Validator {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  private val MinBodyLength = configuration.getInt("messaging.message.minBodyLength").getOrElse(1)
  private val MaxBodyLength = configuration.getInt("messaging.message.maxBodyLength").getOrElse(65535)

  /**
    * Initializes a new instance of the [[Message]] class with the specified JSON.
    *
    * @param json     The message data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Message] = {
    { relaxed match {
      case Some(r) => validateMessage(r)
      case _ => validateMessage
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new Message(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Message]] class with the specified values.
    *
    * @param id           The identifier of the message.
    * @param threadId     The identifier of the thread the message belongs to.
    * @param createdBy    The username of the user that created the message.
    * @param body         The body of the message.
    * @param seqNumber    The sequence number of the message in the thread.
    * @param creationTime The time the message was created.
    * @return             A new instance of the [[Message]] class.
    */
  def apply(
    id: Option[String] = None,
    threadId: Option[String] = None,
    createdBy: Option[String] = None,
    body: Option[String] = None,
    seqNumber: Option[Int] = None,
    creationTime: Option[DateTime] = None
  ): Message = new Message(
    messageWrites.writes(
      id,
      threadId,
      createdBy,
      body,
      seqNumber,
      creationTime
    )
  ) 

  /**
    * Extracts the content of the specified [[Message]].
    *
    * @param message  The [[Message]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `message` is `null`.
    */
  def unapply(message: Message) = {
    if (message eq null) None
    else Some((
      message.id,
      message.threadId,
      message.createdBy,
      message.body,
      message.seqNumber,
      message.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Message]] to/from JSON.
    */
  implicit val messageFormat: Format[Message] = messageFormat(None)
  def messageFormat(relaxed: Option[Boolean]) = new Format[Message] {
    def reads(json: JsValue) = Message(json, relaxed)
    def writes(message: Message) = message.json
  }

  /**
    * Serializes a [[Message]] to JSON.
    * @note Used internally by `apply`.
    */
  private val messageWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'threadId).writeNullable[String] ~
    (__ \ 'createdBy).writeNullable[String] ~
    (__ \ 'body).writeNullable[String] ~
    (__ \ 'seqNumber).writeNullable[Int] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled
  
  /**
    * Validates the JSON representation of a [[Message]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Message]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateMessage = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'threadId).json.pickBranch orEmpty) ~
    ((__ \ 'createdBy).json.pickBranch orEmpty) ~
    ((__ \ 'body).json.pickBranch orEmpty) ~
    ((__ \ 'seqNumber).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[Message]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Message]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateMessage(relaxed: Boolean) = (
    ((__ \ 'body).json.pickBranch(Reads.of[JsString] <~ Reads.minLength[String](
      MinBodyLength
    ) andKeep Reads.of[JsString] <~ Reads.maxLength[String](
      MaxBodyLength
    )) orEmptyIf relaxed)
  )
}
