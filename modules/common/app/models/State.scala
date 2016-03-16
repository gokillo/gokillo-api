/*#
  * @file State.scala
  * @begin 25-Nov-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import services.common.FsmBase

/**
  * Represents the state of an FSM.
  *
  * @constructor  Initializes a new instance of the [[State]] class.
  * @param json   The state data as JSON.
  * @param fsm    An implicit FSM that defines the valid states.
  */
class State protected(json: JsValue)(
  implicit protected var fsm: FsmBase
) extends Status(json) with api.State {

  def copy(state: State): State = super.copy(state).asInstanceOf[State]
  override def copy(json: JsValue): State = super.copy(json).asInstanceOf[State]
}

/**
  * Factory class for creating [[State]] instances.
  */
object State extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError
  import Status._

  /**
    * Initializes a new instance of the [[State]] class with the specified JSON.
    *
    * @param json The state data as JSON.
    * @param fsm  An implicit FSM that defines the valid states.
    * @return     A `JsResult` value that contains the new class
    *             instance, or `JsError` if `json` is not valid.
    */
  def apply(json: JsValue)(implicit fsm: FsmBase): JsResult[State] = {
    validateStatus.reads(json).fold(
      valid = { validated => JsSuccess(new State(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[State]] class with the specified values.
    *
    * @param value      The value of the state.
    * @param timestamp  The time the state was set. Default to now.
    * @param fsm        An implicit FSM that defines the valid states.
    * @return           A new instance of the [[State]] class.
    */
  def apply(
    value: String,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC)
  )(implicit fsm: FsmBase): State = new State(
    statusWrites.writes(
      value,
      timestamp
    )
  )

  /**
    * Extracts the content of the specified [[State]].
    *
    * @param state  The [[State]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `state` is `null`.
    */
  def unapply(state: State) = Status.unapply(state.asInstanceOf[Status])

  /**
    * Serializes/Deserializes a [[State]] to/from JSON.
    * @param fsm  An implicit FSM that defines the valid states.
    */
  implicit def stateFormat(implicit fsm: FsmBase): Format[State] = new Format[State] {
    def reads(json: JsValue) = State(json)
    def writes(state: State) = state.json
  }

  /**
    * Implicitly converts the specified state to a string.
    *
    * @param state  The state to convert.
    * @return       The string converted from `state`.
    */
  implicit def toString(state: State) = state.toString

  /**
    * Validates the JSON representation of a [[State]].
    *
    * @param fsm  An implicit FSM that defines the valid states.
    * @return     A `Reads` that validates the JSON representation of a [[State]].
    * @note       This validator is intended for JSON coming from both inside and
    *             outside the application.
    */
  def validateState(implicit fsm: FsmBase) = (
    ((__ \ 'value).json.pickBranch(Reads.of[JsString] <~ state)) ~
    ((__ \ 'timestamp).json.pickBranch(Reads.of[JsString] <~ isoDateTime))
  ).reduce

  /**
    * Validates the state read by the specified JSON deserializer against
    * the states allowed by the specified FSM.
    *
    * @param fsm    An implicit FSM that defines the valid states.
    * @param reads  An implicit state deserializer.
    * @return       A `JsResult` that contains the deserialized state,
    *               or `JsError` if the state is not valid.
    */
  private def state(implicit fsm: FsmBase, reads: Reads[String]) = {
    Reads[String](js =>
      reads.reads(js).flatMap { value =>
        if (fsm.values.map(s => s.toString).contains(value)) JsSuccess(value)
        else JsError(ValidationError("error.state", fsm.values.mkString("|")))
      }
    )
  }
}
