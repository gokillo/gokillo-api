/*#
  * @file HistoryEvent.scala
  * @begin 3-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._

/**
  * Represents a history event.
  *
  * @constructor  Initializes a new instance of the [[HistoryEvent]] class.
  * @param json   The history event data as JSON.
  */
class HistoryEvent protected(protected var json: JsValue) extends JsModel with api.HistoryEvent {

  def text = json as (__ \ 'text).read[String]
  def text_= (v: String) = setValue((__ \ 'text), Json.toJson(v))
  def detail = json as (__ \ 'detail).readNullable[String]
  def detail_= (v: Option[String]) = setValue((__ \ 'detail), Json.toJson(v))
  def timestamp = json as (__ \ 'timestam).read[DateTime]
  def timestamp_= (v: DateTime) = setValue((__ \ 'timestamp), Json.toJson(v))

  def copy(historyEvent: HistoryEvent): HistoryEvent = new HistoryEvent(this.json.as[JsObject] ++ historyEvent.json.as[JsObject])
  def copy(json: JsValue): HistoryEvent = HistoryEvent(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(historyEvent, _) => historyEvent
    case JsError(_) => new HistoryEvent(this.json)
  }
}

/**
  * Factory class for creating [[HistoryEvent]] instances.
  */
object HistoryEvent extends Validator{

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[HistoryEvent]] class with the specified JSON.
    *
    * @param json     The history event data as JSON.
    * @return         A `JsResult` value that contains the new class instance,
    *                 or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[HistoryEvent] = {
    validateHistoryEvent.reads(json).fold(
      valid = { validated => JsSuccess(new HistoryEvent(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[HistoryEvent]] class with the specified values.
    *
    * @param text       The event text.
    * @param detail     The event detail.
    * @param timestamp  The time the event occurred.
    * @return           A new instance of the [[HistoryEvent]] class.
    */
  def apply(
    text: String,
    detail: Option[String] = None,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC)
  ): HistoryEvent = new HistoryEvent(
    historyEventWrites.writes(
      text,
      detail,
      timestamp
    )
  ) 

  /**
    * Extracts the content of the specified [[HistoryEvent]].
    *
    * @param historyEvent The [[HistoryEvent]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `historyEvent` is `null`.
    */
  def unapply(historyEvent: HistoryEvent) = {
    if (historyEvent eq null) None
    else Some((
      historyEvent.text,
      historyEvent.detail,
      historyEvent.timestamp
    ))
  }

  /**
    * Serializes/Deserializes a [[HistoryEvent]] to/from JSON.
    */
  implicit val historyEventFormat = new Format[HistoryEvent] {
    def reads(json: JsValue) = HistoryEvent(json)
    def writes(historyEvent: HistoryEvent) = historyEvent.json
  }

  /**
    * Serializes a [[HistoryEvent]] to JSON.
    * @note Used internally by `apply`.
    */
  private val historyEventWrites = (
    (__ \ 'text).write[String] ~
    (__ \ 'detail).writeNullable[String] ~
    (__ \ 'timestamp).write[DateTime]
  ).tupled
  
  /**
    * Validates the JSON representation of a [[HistoryEvent]].
    *
    * @return A `Reads` that validates the JSON representation of a [[HistoryEvent]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateHistoryEvent = (
    ((__ \ 'text).json.pickBranch) ~
    ((__ \ 'detail).json.pickBranch orEmpty) ~
    ((__ \ 'timestamp).json.pickBranch(Reads.of[JsString] <~ isoDateTime))
  ).reduce
}
