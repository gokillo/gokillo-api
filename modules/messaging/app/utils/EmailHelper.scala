/*#
  * @file EmailHelper.scala
  * @begin 18-Sep-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.messaging

import scala.concurrent.Future
import play.api.i18n.Messages
import utils.common.EmailHelperBase
import models.auth.User
import models.messaging.{Thread, Message}

/**
  * Provides functionality for sending notifications when a user triggers an event
  * in a thread.
  */
object EmailHelper extends EmailHelperBase {

  /**
    * Sends a ''new post in thread'' notification to the specified recipient.
    *
    * @param recipient  The user to send the notification to.
    * @param thread     The thread in which the new post was created
    * @param message    The new post created in `thread`.
    * @return           A `Future` containing the message id.
    */
  def sendNewPostInThreadEmail(recipient: User, thread: Thread, message: Message): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(recipient.email.get),
      Messages("messaging.email.newPostInThread.subject", thread.subject.get),
      views.html.newPostInThreadEmail(recipient, thread, message)(lang(recipient.lang))
    )
  }

  /**
    * Sends an ''update in thread'' notification to the specified recipient.
    *
    * @param recipient  The user to send the notification to.
    * @param thread     The thread in which the post was updated.
    * @param message    The post updated in `thread`.
    * @return           A `Future` containing the message id.
    */
  def sendUpdateInThreadEmail(recipient: User, thread: Thread, message: Message): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(recipient.email.get),
      Messages("messaging.email.updateInThread.subject", thread.subject.get),
      views.html.updateInThreadEmail(recipient, thread, message)(lang(recipient.lang))
    )
  }

  /**
    * Sends a ''removal in thread'' notification to the specified recipient.
    *
    * @param recipient  The user to send the notification to.
    * @param thread     The thread in which the post was removed.
    * @param message    The post removed in `thread`.
    * @return           A `Future` containing the message id.
    */
  def sendRemovalInThreadEmail(recipient: User, thread: Thread, message: Message): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(recipient.email.get),
      Messages("messaging.email.removalInThread.subject", thread.subject.get),
      views.html.removalInThreadEmail(recipient, thread, message)(lang(recipient.lang))
    )
  }
}
