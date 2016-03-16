/*#
  * @file Peer.scala
  * @begin 6-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import PeerType._

/**
  * Represents a peer application.
  *
  * @constructor        Initializes a new instance of the [[Peer]] class.
  * @param name         The name of the peer application.
  * @param url          The URL of the peer application.
  * @param peerType     One of the [[PeerType]] values.
  * @param description  The description of the peer application.
  * @param paths        The paths to be associated with `url`.
  */
class Peer private(
  val name: String,
  val url: SimpleUrl,
  val peerType: PeerType,
  val description: String,
  val paths: Map[String, String]
) {

  /** Gets the endpoint for the path identified by the specified name. */
  def endPoint(pathName: String) = url.toString(paths(pathName))
}

/**
  * Factory class for creating [[Peer]] instances.
  */
object Peer {

  import scala.collection.JavaConversions._
  import play.api.Configuration
  import play.api.Play.current

  /**
    * Initializes a new instance of the [[Peer]] class for each peer
    * application defined in the specified configuration.
    *
    * @param parents  The name of the elements that group peer elements.
    * @param children The name of the elements that group path elements.
    * @param config The application configuration.
    * @return       A `Map` containing the new instances of the [[Peer]] class.
    */
  def apply(parents: String, children: String, config: Configuration): Map[String, Peer] = {
    var peers = scala.collection.mutable.Map[String, Peer]().withDefaultValue(default)

    config.getConfigList(parents).foreach { _.toList.foreach { config =>
      config.getString("name").foreach { name =>
      config.getString("url").foreach { url =>
        val peerUrl = SimpleUrl(Some(url))
        val peerType = PeerType(config.getString("type").getOrElse(""))
        val description = config.getString("description").getOrElse("")
        var paths = scala.collection.mutable.Map[String, String]().withDefaultValue("")
        config.getConfigList(children).foreach { _.toList.foreach { config =>
          config.getString("name").foreach { name =>
          config.getString("value").foreach { value =>
            paths += (name -> value)
          }}
        }}
        peers += (name -> new Peer(name, peerUrl, peerType, description, paths.toMap))
      }}
    }}

    peers.toMap
  }

  /**
    * Initializes a default instance of the [[Peer]] class.
    * @return A default instance of the [[Peer]] class.
    */
  private def default = new Peer(
    "", SimpleUrl(None), Undefined, "", Map[String, String]().withDefaultValue("")
  )
}
