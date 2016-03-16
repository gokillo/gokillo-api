/*#
  * @file common.scala
  * @begin 22-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils

package object common {

  import scala.util.Try
  import scala.util.control.NonFatal

  object env {

    import play.api.Configuration
    import play.api.Play
    import play.api.Play.current

    private var _config: Configuration = _
    private var _localhost: SimpleUrl = _
    private var _peers: Map[String, Peer] = _

    final val ApiDocs = "gokillo-apidocs"
    final val Assets = "gokillo-assets"
    final val WebApp = "gokillo-ui"

    /**
      * Gets the URL of the local application.
      * @return The URL of the local application.
      */
    def localhost: SimpleUrl = {
      /*
      def loadFromConfig = {
        val ssl = _config.getBoolean("ssl").getOrElse(false)
        val host = _config.getString("host").getOrElse(EndPoint.DefaultHostName)
        val port = Play.isTest match {
          case false => System.getProperty("http.port", null) match {
              case port if port != null => parse[Int](port).getOrElse(EndPoint.DefaultPort)
              case _ => EndPoint.DefaultPort
            }
          case _ => System.getProperty("testserver.port", null) match {
              case port if port != null => parse[Int](port).getOrElse(EndPoint.DefaultTestPort)
              case _ => EndPoint.DefaultTestPort
            }
        }
        SimpleUrl(ssl, EndPoint(host, port))
      }
      */

      if (_localhost == null) this.synchronized {
        if (_localhost == null) _localhost = SimpleUrl(_config.getString("swagger.api.basepath"))
      }

      _localhost
    }

    /** Gets all the peer applications. */
    def peers = _peers

    /**
      * Initializes environment information according to the specified
      * configuration.
      *
      * @param config The application configuration.
      * @note         `initEnv` is invoked by `Global.onLoadConfig` just
      *               after configuration has been loaded but before the
      *               application actually starts.
      */
    def initEnv(config: Configuration) = {
      _peers = Peer("peers", "paths", config)
      _config = config
      _config
    }
  }

  /**
    * Executes the specified code and releases any resource it uses when done.
    *
    * @tparam A The type of the resource used by `code` and released by `cleanup`.
    * @tparam B The type of the value returned by `code`.
    *
    * @param resource The resource used by `code`.
    * @param cleanup  The function that releases `resource` when `code` completes.
    * @param code     The code that uses `resource`.
    * @return         A `Try` value containing the value returned by `code`,
    *                 or `Failure` in case of error.
    */
  def cleanly[A, B](resource: => A)(cleanup: A => Unit)(code: A => B) = Try {
    val r = resource
    try { code(r) } finally { cleanup(r) }
  }

  case class ParseOp[T](op: String => T)
  implicit val popBoolean = ParseOp[Boolean](_.toBoolean)
  implicit val popDouble = ParseOp[Double](_.toDouble)
  implicit val popFloat = ParseOp[Float](_.toFloat)
  implicit val popInt = ParseOp[Int](_.toInt)
  implicit val popShort = ParseOp[Short](_.toShort)

  /**
    * Parses the specified string as a value of type `T`.
    *
    * @param s  The string to parse.
    * @tparam T The type to convert `s` to.
    * @return   An Option value containing the converted value,
    *           or `None` if `s` could not be converted.
    */
  def parse[T: ParseOp](s: String) = try {
    Some(implicitly[ParseOp[T]].op(s))
  } catch { case NonFatal(_) => None }
}
