/*#
  * @file SimpleUrl.scala
  * @begin 22-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

/**
  * Represents a pointer to a resource on the web.
  *
  * @constructor    Initializes a new instance of the [[SimpleUrl]] class.
  * @param scheme   The scheme component of the URL.
  * @param endPoint The host and port components of the URL.
  * @param path     The path component of the URL.
  */
class SimpleUrl protected(val scheme: String, val endPoint: EndPoint, val path: String = "") {

  /** Gets the root of this URL. */
  def root = s"""$scheme://${endPoint.toString}"""

  /**
    * Applies the specified path to this URL and returns it as a string.
    *
    * @param path The path to apply to this URL.
    * @return     The resulting URL as a string.
    */
  def toString(path: String): String = s"""$root${(if (path.startsWith("/")) "" else "/")}$path"""

  /** Gets this URL as a string. */
  override def toString: String = toString(path)
}

/**
  * Factory class for creating [[SimpleUrl]] instances.
  */
object SimpleUrl {

  /**
    * Initializes a new instance of the [[SimpleUrl]] class with the specified
    * scheme and port.
    *
    * @param scheme The scheme component of the URL.
    * @param port   The port component of the URL.
    * @return       A new instance of the [[SimpleUrl]] class.
    */
  def apply(scheme: String, port: Int, secure: Boolean): SimpleUrl = new SimpleUrl(scheme, EndPoint(port))

  /**
    * Initializes a new instance of the [[SimpleUrl]] class with the specified
    * scheme and endpoint.
    *
    * @param scheme   The scheme component of the URL.
    * @param endPoint The host and port components of the URL.
    * @return         A new instance of the [[SimpleUrl]] class.
    */
  def apply(scheme: String, endPoint: EndPoint): SimpleUrl = new SimpleUrl(scheme, endPoint)

  /**
    * Initializes a new instance of the [[SimpleUrl]] class with the specified
    * scheme, endpoint, and path.
    *
    * @param scheme   The scheme component of the URL.
    * @param endPoint The host and port components of the URL.
    * @param path     The path component of the URL.
    * @return         A new instance of the [[SimpleUrl]] class.
    */
  def apply(scheme: String, endPoint: EndPoint, path: String): SimpleUrl = new SimpleUrl(scheme, endPoint, path)

  /**
    * Initializes a new instance of the [[SimpleUrl]] class with the specified
    * Boolean value indicating whether or not SSL in enabled, endpoint, and path.
    *
    * @param secure   `true` if SSL is enabled; otherwise, `false`.
    * @param endPoint The host and port components of the URL.
    * @param path     The path component of the URL.
    * @return         A new instance of the [[SimpleUrl]] class.
    * @note           If `secure` is `true`, then `scheme` is set to ''https'', otherwise to ''http''.
    */
  def apply(secure: Boolean, endPoint: EndPoint, path: String = ""): SimpleUrl = new SimpleUrl(
    if (secure) "https" else "http", endPoint, path
  )

  /**
    * Initializes a new instance of the [[SimpleUrl]] class with the specified URL.
    *
    * @param spec An `Option` value containing the string to parse as URL.
    * @return     A new instance of the [[SimpleUrl]] class.
    */
  def apply(spec: Option[String]): SimpleUrl = {
    val t = spec match {
      case Some(spec) => parse(spec)
      case _ => ("http", EndPoint(), "")
    }
    new SimpleUrl(t._1, t._2, t._3)
  }

  /**
    * Parses the specified URL to extract scheme, endpoint, and path.
    *
    * @param spec The string to parse as URL.
    * @return     A `Tuple3` containing the scheme, endpoint, and path.
    */
  private def parse(spec: String): (String, EndPoint, String) = {
    import java.net.URI

    val uri = new URI(spec);
    var port = uri.getPort; if (port < 0) port = 80
    (uri.getScheme, EndPoint(uri.getHost, port), uri.getPath)
  }
}
