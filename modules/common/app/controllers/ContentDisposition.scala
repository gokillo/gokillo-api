/*#
 * @file ContentDisposition.scala
 * @begin 12-May-2014
 * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
 * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
 */

package controllers.common

/**
  * Defines MIME content dispositions.
  */
object ContentDisposition extends Enumeration {

  type ContentDisposition = Value

  /**
    * Content is displayed automatically.
    */
  val Inline = Value("inline")

  /**
    * Content is not displayed automatically.
    */
  val Attachment = Value("attachment")
}
