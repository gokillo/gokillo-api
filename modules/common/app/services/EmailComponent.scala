/*#
 * @file EmailComponent.scala
 * @begin 27-Dec-2013
 * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
 * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
 */

package services.common

import scala.util.{Success, Failure}
import java.nio.charset.{StandardCharsets => SC}
import play.api.Logger
import play.twirl.api.Html
import play.utils.UriEncoding
import brix.util.GZip
import utils.common.env._
import models.common.Contact

/**
  * Defines functionality for dealing with email content.
  */
trait EmailComponent {

  /**
    * Returns an instance of a [[RichBody]] implementation.
    */
  def richBody: RichBody

  /**
    * Represents a rich email body.
    */
  trait RichBody {

    /**
      * Creates a rich body with the specified sender information,
      * subject, and body.
      *
      * @param sender   The email sender.
      * @param subject  The email subject.
      * @param body     The email body in HTML format.
      * @return         A rich body made out of `sender`, `subject`, and `body`.
      */
    def apply(sender: Contact, subject: String, body: Html): Html 
  }
}

/**
  * Implements a default `EmailComponent`.
  */
trait DefaultEmailComponent extends EmailComponent {

  def richBody = new DefaultRichBody

  class DefaultRichBody extends RichBody {

    def apply(sender: Contact, subject: String, body: Html): Html = {
      implicit val emailUrl = GZip.deflate((views.html.email(sender, subject)(body)).body) match {
        case Success(deflated) =>
          val encoded = UriEncoding.encodePathSegment(deflated, SC.US_ASCII.name)
          val encodedUrl = s"""${peers(WebApp).endPoint("viewInBrowser")}?email=$encoded"""
          Logger.debug(s"email url: $encodedUrl")
          Some(encodedUrl)
        case Failure(e) =>
          Logger.warn(s"error zipping email into url", e)
          None
      }
      views.html.email(sender, subject)(body)
    }
  }
}
