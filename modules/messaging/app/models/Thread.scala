/*#
  * @file Thread.scala
  * @begin 14-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.messaging

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.auth.User.MinUsernameLength
import models.common.{JsEntity, RefId}
import models.common.RefId._

/**
  * Represents a message thread.
  *
  * @constructor  Initializes a new instance of the [[Thread]] class.
  * @param json   The thread data as JSON.
  */
class Thread protected(protected var json: JsValue) extends JsEntity with api.Thread {

  def refId = json as (__ \ 'refId).readNullable[RefId]
  def refId_= (v: Option[RefId]) = setValue((__ \ 'refId), Json.toJson(v))
  def subject = json as (__ \ 'subject).readNullable[String]
  def subject_= (v: Option[String]) = setValue((__ \ 'subject), Json.toJson(v))
  def createdBy = json as (__ \ 'createdBy).readNullable[String]
  def createdBy_= (v: Option[String]) = setValue((__ \ 'createdBy), Json.toJson(v))
  def confidentialOpt = json as (__ \ 'confidential).readNullable[Boolean]
  def confidentialOpt_= (v: Option[Boolean]) = setValue((__ \ 'confidential), Json.toJson(v))
  def confidential = confidentialOpt.getOrElse(false)
  def confidential_= (v: Boolean) = confidentialOpt_=(Some(v))
  def grantees = json as (__ \ 'grantees).readNullable[List[String]]
  def grantees_= (v: Option[List[String]]) = setValue((__ \ 'grantees), Json.toJson(v))
  def messageCount = json as (__ \ 'messageCount).readNullable[Int]
  def messageCount_= (v: Option[Int]) = setValue((__ \ 'messageCount), Json.toJson(v))
  def lastActivityTime = json as (__ \ 'lastActivityTime).readNullable[DateTime]
  def lastActivityTime_= (v: Option[DateTime]) = setValue((__ \ 'lastActivityTime), Json.toJson(v))

  def copy(thread: Thread): Thread = new Thread(this.json.as[JsObject] ++ thread.json.as[JsObject])
  def copy(json: JsValue): Thread = Thread(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(thread, _) => thread
    case JsError(_) => new Thread(this.json)
  }
}

/**
  * Factory class for creating [[Thread]] instances.
  */
object Thread extends Validator {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._
  import models.common.RefId._

  private val MinSubjectLength = configuration.getInt("messaging.thread.minSubjectLength").getOrElse(1)
  private val MaxSubjectLength = configuration.getInt("messaging.thread.maxSubjectLength").getOrElse(150)

  /**
    * Initializes a new instance of the [[Thread]] class with the specified JSON.
    *
    * @param json     The thread data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Thread] = {
    { relaxed match {
      case Some(r) => validateThread(r)
      case _ => validateThread
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new Thread(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Thread]] class with the specified values.
    *
    * @param id           The identifier of the thread.
    * @param refId        A reference to the object associated with the thread.
    * @param subject      The subject of the thread.
    * @param createdBy    The username of the user that created the thread.
    * @param confidential A Boolean value indicating whether or not the thread is confidential.
    * @param grantees     The username of the users allowed to participate in the thread.
    * @param messageCount The number of messages in the thread.
    * @param lastActivityTime The time the last activity happened on the thread.
    * @param creationTime The time the thread was created.
    * @return             A new instance of the [[Thread]] class.
    */
  def apply(
    id: Option[String] = None,
    refId: Option[RefId] = None,
    subject: Option[String] = None,
    createdBy: Option[String] = None,
    confidential: Option[Boolean] = None,
    grantees: Option[List[String]] = None,
    messageCount: Option[Int] = None,
    lastActivityTime: Option[DateTime] = None,
    creationTime: Option[DateTime] = None
  ): Thread = new Thread(
    threadWrites.writes(
      id,
      refId,
      subject,
      createdBy,
      confidential,
      grantees,
      messageCount,
      lastActivityTime,
      creationTime
    )
  ) 

  /**
    * Extracts the content of the specified [[Thread]].
    *
    * @param thread The [[Thread]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `thread` is `null`.
    */
  def unapply(thread: Thread) = {
    if (thread eq null) None
    else Some((
      thread.id,
      thread.refId,
      thread.subject,
      thread.createdBy,
      thread.confidential,
      thread.grantees,
      thread.messageCount,
      thread.lastActivityTime,
      thread.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Thread]] to/from JSON.
    */
  implicit val threadFormat: Format[Thread] = threadFormat(None)
  def threadFormat(relaxed: Option[Boolean]) = new Format[Thread] {
    def reads(json: JsValue) = Thread(json, relaxed)
    def writes(thread: Thread) = thread.json
  }

  /**
    * Serializes a [[Thread]] to JSON.
    * @note Used internally by `apply`.
    */
  private val threadWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'refId).writeNullable[RefId] ~
    (__ \ 'subject).writeNullable[String] ~
    (__ \ 'createdBy).writeNullable[String] ~
    (__ \ 'confidential).writeNullable[Boolean] ~
    (__ \ 'grantees).writeNullable[List[String]] ~
    (__ \ 'messageCount).writeNullable[Int] ~
    (__ \ 'lastActivityTime).writeNullable[DateTime] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled
  
  /**
    * Validates the JSON representation of the reference id.
    */
  private val refId = (__ \ 'refId).json.pick[JsValue] andThen validateRefId

  /**
    * Validates the JSON representation of the users allowed to participate in the thread.
    */
  private val validateGrantees = verifyingIf((arr: JsArray) => arr.value.nonEmpty)(Reads.list(minLength[String](MinUsernameLength)))
  private val grantees: Reads[JsArray] = (__ \ 'grantees).json.pick[JsArray] andThen validateGrantees

  /**
    * Validates the JSON representation of a [[Thread]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Thread]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateThread = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'refId).json.copyFrom(refId) orEmpty) ~
    ((__ \ 'subject).json.pickBranch orEmpty) ~
    ((__ \ 'createdBy).json.pickBranch orEmpty) ~
    ((__ \ 'confidential).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'grantees).json.copyFrom(grantees) orEmpty) ~
    ((__ \ 'messageCount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'lastActivityTime).json.pickBranch orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[Thread]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Thread]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateThread(relaxed: Boolean) = (
    ((__ \ 'refId).json.copyFrom(refId) orEmptyIf relaxed) ~
    ((__ \ 'subject).json.pickBranch(Reads.of[JsString] <~ Reads.minLength[String](
      MinSubjectLength
    ) andKeep Reads.of[JsString] <~ Reads.maxLength[String](
      MaxSubjectLength
    )) orEmptyIf relaxed) ~
    ((__ \ 'confidential).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'grantees).json.copyFrom(grantees) orEmpty)
  ).reduce
}
