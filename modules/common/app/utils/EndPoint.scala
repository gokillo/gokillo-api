/*#
  * @file EndPoint.scala
  * @begin 22-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

/**
  * Represents an application endpoint.
  *
  * @constructor    Initializes a new instance of the [[EndPoint]] class.
  * @param hostName The name of the server that hosts the application.
  * @param port     The TCP/IP port.
  */
class EndPoint private(val hostName: String, val port: Int) {

  import EndPoint._

  /**
    * Returns a `String` representation of the [[EndPoint]].
    * @return A `String` representation of the [[EndPoint]].
    */
  implicit override def toString = s"""$hostName${(if (port != DefaultPort) ":" + port else "")}"""
}

/**
  * Factory class for creating [[EndPoint]] instances.
  */
object EndPoint {

  import utils.common.typeExtensions._

  final val DefaultHostName = "localhost"
  final val DefaultPort = 80
  final val DefaultTestPort = 19001

  /**
    * Initializes a new instance of the [[EndPoint]] class with the specified
    * application host.
    *
    * @param host The application host in `hostname:port` format.
    * @return     A new instance of the [[EndPoint]] class.
    */
  def apply(host: Option[String] = None): EndPoint = {
    var hostName = DefaultHostName
    var port = DefaultPort

    host.foreach { _host => if (_host.isHostName) {
      val r = _host.split(":")
      hostName = r(0)
      if (r.length > 1) port = r(1).toInt
    }}

    apply(hostName, port)
  }

  /**
    * Initializes a new instance of the [[EndPoint]] class with the specified
    * port.
    *
    * @param port The TCP/IP port.
    * @return     A new instance of the [[EndPoint]] class.
    */
  def apply(port: Int): EndPoint = new EndPoint(DefaultHostName, port)

  /**
    * Initializes a new instance of the [[EndPoint]] class with the specified
    * host name and port.
    *
    * @param hostName The name of the server that hosts the application.
    * @param port     The TCP/IP port.
    * @return         A new instance of the [[EndPoint]] class.
    */
  def apply(hostName: String, port: Int): EndPoint = new EndPoint(hostName, port)
}
