/*#
  * @file MessageBroker.scala
  * @begin 23-Sep-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import scala.concurrent.Future
import akka.actor._
import akka.camel._
import play.api.libs.json.JsValue

/** Represents a JSON message sent/received to/from a destination. */
case class JsMessage(body: JsValue)

/**
  * Provides functionality for interacting with the underlaying messaging
  * and integration patterns system.
  *
  * @constructor    Creates a new instance of the [[MessageBroker]] class with
  *                 the specified module name.
  * @param module   The module the new [[MessageBroker]] instance is associated with.
  * @param instance The broker instance to connect.
  * @return         A new instance of the [[MessageBroker]] class.
  */
class MessageBroker private(module: String, instance: String) {

  import scala.collection.mutable.{Map => MutableMap}
  import java.util.UUID
  import org.apache.activemq.camel.component.ActiveMQComponent
  import play.api.Play.current
  import play.api.Play.configuration
  import services.common.CommonErrors._
  import utils.common.{Peer, PeerType}

  @inline private final val Scheme = "nio"
  private var self: Peer = _
  private val actorSystem = ActorSystem(s"$module-messageBroker-$instance")
  private val system = CamelExtension(actorSystem)

  /** Gets the message producers associated with this `MessageBroker`. */
  private var _producers: Map[String, ActorRef] = _
  def producers = _producers

  /** Gets the message consumers associated with this `MessageBroker`. */
  private val _consumers = MutableMap[String, (String, ActorRef)]()
  def consumers = _consumers.toMap

  init

  /** Initializes this instance of the message broker. */
  private def init {
    configuration.getConfig(module).map { config =>
      self = Peer("messageBrokers", "destinations", config)(instance)
      if (self.url.scheme != Scheme) throw MissingConfig(s"$module.messageBrokers")

      // add ActiveMQ to the Camel extension
      system.context.addComponent("activemq", ActiveMQComponent.activeMQComponent(self.url.toString))

      // create a message producer for each destination
      val actorRefs = MutableMap[String, ActorRef]()
      self.paths.foreach {
        case (key, value) => actorRefs(key) = actorSystem.actorOf(Props(new JsMessageProducer(value)))
      }
      _producers = actorRefs.toMap
    } orElse { throw MissingConfig(module) }
  }

  /**
    * Stops the message broker.
    */
  def shutdown = actorSystem.shutdown

  /**
    * Creates a `MessageConsumer` that receives messages from the specified destination.
    *
    * @param destinationName  The name of the destination to receive messages from.
    * @param callback         The callback to be invoked when a message is received.
    */
  def createConsumer(destinationName: String, callback: JsValue => Future[Unit]): Unit = {
    self.paths(destinationName) match {
      case destination if destination == "" => throw NotDefined("destination", destinationName)
      case destination => _consumers(UUID.randomUUID.toString) = (
        destinationName, actorSystem.actorOf(Props(new JsMessageConsumer(destination, callback)))
      )
    }
  }
}

/**
  * Factory class for creating [[MessageBroker]] instances.
  */
object MessageBroker {

  import utils.common.SimpleUrl

  /**
    * Initializes a new instance of the [[MessageBroker]] class with the
    * specified module and broker instance.
    *
    * @param moudule  The module the new [[MessageBroker]] instance is associated with.
    * @param instance The broker instance to connect.
    * @return         A new instance of the [[MessageBroker]] class.
    */
  def apply(module: String, instance: String = "default") = new MessageBroker(module, instance)
}

/**
  * Represents a producer of JSON messages.
  *
  * @constructor        Initializes a new instance of the [[JsMessageProducer]] class
  *                     with the specified destination.
  * @param destination  The destination to send messages to.
  */
class JsMessageProducer(destination: String) extends Actor with Producer with Oneway {

  /** Gets the destination endpoint. */
  def endpointUri = s"activemq:$destination"
}

/**
  * Represents a consumer of JSON messages.
  *
  * @constructor        Initializes a new instance of the [[JsMessageConsumer]] class
  *                     with the specified destination and callback.
  * @param destination  The destination to receive messages from.
  * @param callback     The callback to be invoked when a message is received.
  */
class JsMessageConsumer(
  destination: String,
  private val callback: JsValue => Future[Unit]
) extends Actor with Consumer {

  /** Gets the destination endpoint. */
  def endpointUri = s"activemq:$destination"

  /** Called when a message is received. */
  def receive = {
    case msg: CamelMessage => callback(msg.bodyAs[JsMessage].body)
  }
}
