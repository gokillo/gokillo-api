/*#
  * @file FsmBase.scala
  * @begin 25-Nov-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import play.api.libs.json._
import utils.common.typeExtensions._
import CommonErrors._
import FsmErrors._

/**
  * Provides base functionality for any finite state machine (FSM) implementation.
  */
trait FsmBase extends Enumeration { thisenum =>

  type Ztate = Value

  /**
    * Represents a conversion from a state to another.
    */
  trait FsmTransduction extends Dynamic {

    import scala.concurrent.Future
    import scala.language.dynamics
    import scala.collection.mutable.{Map => MutableMap}

    // dynamic fields
    private val dynamic = MutableMap.empty[String, Option[AnyRef]].withDefaultValue(None)

    /**
      * Returns a `Future` value containing the exception raised when the
      * specified operation is not allowed.
      */
    protected def operationNotAllowed(message: FsmMessage) = Future.failed(
      NotAllowed("operation", message.name, s"fsm state is ${state.toString}")
    )

    /**
      * Gets the state this `FsmTransduction` is associated with.
      * @return One of the [[Ztate]] values.
      */
    def state: Ztate

    /**
      * Adds the specified dynamic field to this `Transduction`.
      *
      * @param name   The name of the field to add.
      " @param value  The value of `name`.
      */
    def updateDynamic(name: String)(value: Option[AnyRef]) {
      dynamic(name) = value
    }

    /**
      * Returns the value of the specified dynamic field.
      *
      * @param name The name of the field to retrieve.
      * @return     The value of `name`.
      */
    def selectDynamic(name: String) = dynamic(name)
  }

  /**
    * Represents a message sent to the FSM.
    */
  trait FsmMessage {

    /**
      * Gets the name of the message.
      * @return The name of the message.
      */
    def name = {
      val className = this.getClass.getName
      className.substring(className.lastIndexOf("$") + 1)
    }
  }

  /**
    * Holds the current state and provides functionality for transducing to other states.
    *
    * @constructor        Initializes a new instance of the [[FsmBase#Val]] class.
    * @param name         The name of the state.
    * @param convertWith  The `FsmTransduction` that actually converts the current state.
    */
  protected abstract class Val(name: String, val convertWith: FsmTransduction) extends super.Val(name)

  /**
    * Returns a new instance of the [[FsmBase#Val]] class.
    *
    * @param name         The name of the state.
    * @param convertWith  The `FsmTransduction` that converts a state to another.
    * @return             A new instance of the [[FsmBase#Val]] class.
    * @note               Derived classes must implement this method to provide the
    *                     actual `FsmTransduction` implementation.
    */
  protected def Val(name: String, convertWith: FsmTransduction): Val

  /**
    * Initializes a new state `Value` with the specified name and transduction.
    *
    * @param name         The name of the state.
    * @param convertWith  The `FsmTransduction` that converts a state to another.
    * @return             A new instance of the [[FsmBase#Val]] class.
    */
  protected def Value(name: String, convertWith: FsmTransduction) = Val(name, convertWith)

  /**
    * Implicitly converts the specified state to a string.
    *
    * @param state  The state to convert.
    * @return       The string converted from `state`.
    */
  implicit def toString(state: Ztate) = state.toString

  /**
    * Implicitly converts the specified state to an `Option[String]`.
    *
    * @param state  The state to convert.
    * @return       The `Option[String]` converted from `state`.
    */
  implicit def toOption(state: Ztate) = Some(state.toString)

  /**
    * Serializes/Deserializes a state to/from JSON.
    */
  implicit val ztateFormat = new Format[Ztate] {
    def reads(json: JsValue) = JsSuccess(Value(json.as[JsString].value))
    def writes(state: Ztate) = JsString(state.toString)
  }

  /**
    * Returns the state with the specified name.
    *
    * @param name The name of the state.
    * @return     The state whose name is `name`.
    */
  def apply(name: String): Ztate = {
    try {
      return thisenum.withName(name.uncapitalize).asInstanceOf[Val]
    } catch {
      case e: NoSuchElementException => throw NotSupported("state", name)
    }
  }
}
