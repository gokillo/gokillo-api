/*#
  * @file PeerType.scala
  * @begin 29-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines peer types.
  */
object PeerType extends Enumeration {

  type PeerType = Value

  /**
    * Defines a peer application that is a native API consumer.
    */
  val NativeApiConsumer = Value("nativeApiConsumer")

  /**
    * Defines a peer application that is a foreign API consumer.
    */
  val ForeignApiConsumer = Value("foreignApiConsumer")

  /**
    * Defines a peer application of an undefined type.
    */
  val Undefined = Value("undefined")

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): PeerType = {
    try {
      name match {
        case "" | null => Undefined
        case _ => PeerType.withName(name.uncapitalize)
      }
    } catch { case e: NoSuchElementException =>
      throw NotSupported("peer type", name)
    }
  }
}
