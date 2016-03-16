/*#
 * @file EmailServiceComponent.scala
 * @begin 27-Dec-2013
 * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
 * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
 */

package services.common

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current
import play.api.Play.configuration
import play.api.i18n.Messages
import play.twirl.api.Html
import models.common.Contact
import utils.common.Emailer

/**
  * Defines functionality for email services.
  */
trait EmailServiceComponent {

  /**
    * Returns an instance of an `EmailService` implementation.
    */
  def emailService: EmailService

  /**
    * Represents an email service.
    */
  trait EmailService {

    /**
      * Sends the specified email body to the specified recipients.
      *
      * @param sender   The email sender.
      * @param recipients A sequence of one or more recipients.
      * @param subject  The email subject.
      * @param body     The email body in `Html` format.
      * @return         A `Future` containing the message id.
      * @note           `body` just contains some custom `Html` to be enriched by the injected
      *                 `EmailComponent`. If `sender` is `None`, then the default agent is assumed.
      */
    def sendEmail(
      sender: Option[Contact], recipients: Seq[String], subject: String, body: Html
    ): Future[String]
  }
}

/**
  * Implements a default `EmailServiceComponent`.
  */
trait DefaultEmailServiceComponent extends EmailServiceComponent {
  this: EmailComponent =>

  def emailService = new DefaultEmailService

  class DefaultEmailService extends EmailService {

    def sendEmail(
      sender: Option[Contact], recipients: Seq[String], subject: String, body: Html
    ): Future[String] = {
      val actualSender = sender.getOrElse(Contact(
        configuration.getString("common.emails.agent").getOrElse(""),
        Some(Messages("common.role.agent")))
      )

      Emailer.sendEmail(
        actualSender.namedEmail,
        recipients,
        Some(subject),
        (None, Some(richBody(actualSender, subject, body)))
      )
    }
  }
}
