/*#
  * @file Emailer.scala
  * @begin 15-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.concurrent.Future
import scala.util.control.NonFatal
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer._
import play.api.Logger
import play.api.Play.current
import play.twirl.api.{Html, Txt}

/**
  * Provides functionality for sending emails.
  */
object Emailer {

  private final val MaxRecipients = 5

  /**
    * Sends the specified email body to the specified recipients.
    *
    * @param from       The email address of the sender.
    * @param recipients A sequence of one or more recipients.
    * @param subject    The email subject.
    * @param body       The email body in either `Txt` or `Html` format, or both.
    * @return           A `Future` containing the message id.
    *
    * Usage example:
    *
    * {{{
    * import utils.common.Emailer
    * 
    * Emailer.sendEmail(
    *   "Sender Name <sender.name@domain.com>",
    *   Seq("Receiver Name <receiver.name@domain.com>"),
    *   "Hello",
    *   (Some(Txt("How are you?")), Some(Html("<body>How are you?</body>")))) 
    * }}}
    */
  def sendEmail(
    from: String, recipients: Seq[String],
    subject: Option[String] = None, body: (Option[Txt], Option[Html])
  ): Future[String] = {
    val futureEmail = Future {
      MailerPlugin.send(Email(
        subject = subject.getOrElse(""),
        from = from,
        to = if (recipients.length > 1) Seq.empty else recipients,
        bcc = if (recipients.length > 1) recipients else Seq.empty,
        bodyText = body._1.map(_.body),
        bodyHtml = body._2.map(_.body)
      ))
    }

    futureEmail.onFailure {
      case NonFatal(e) => Logger.warn(s"error sending email to ${printRecipients(recipients)}", e)
    }

    futureEmail.onSuccess {
      case _ => Logger.debug(s"email to ${printRecipients(recipients)} sent successfully")
    }

    futureEmail
  }

  /**
    * Prints the specified recipients.
    *
    * @param recipients The recipients to print.
    * @return           A string containing a list of recipients, up to `MaxRecipients`.
    */
  private def printRecipients(recipients: Seq[String]): String = {
    recipients.take(MaxRecipients).mkString(", ") + { recipients.length match {
      case length if length > MaxRecipients => ", ..."
      case _ => ""
    }}
  }
}
