/*#
  * @file EmailHelper.scala
  * @begin 6-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.auth

import scala.concurrent.Future
import play.api.i18n.Messages
import utils.common.EmailHelperBase
import utils.common.typeExtensions._
import models.auth.User

/**
  * Provides functionality for sending emails during user registration
  * or account management activities.
  */
object EmailHelper extends EmailHelperBase {

  /**
    * Sends an email verification email to the specified user.
    *
    * @param user   The user to send the verification email to.
    * @param jwt    The JSON Web Token that enables account activation.
    * @return       A `Future` containing the message id.
    */
  def sendEmailVerificationEmail(user: User, jwt: String): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.emailVerification.subject"),
      views.html.emailVerificationEmail(user, jwt)(lang(user.lang))
    )
  }

  /**
    * Sends a password change notification email to the specified user.
    *
    * @param user   The user to send the notification email to.
    * @return       A `Future` containing the message id.
    */
  def sendPasswordChangeNotificationEmail(user: User): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.passwordChangeNotification.subject"),
      views.html.passwordChangeNotificationEmail(user)(lang(user.lang))
    )
  }

  /**
    * Sends a password reset email to the specified user.
    *
    * @param user   The user to send the password reset email to.
    * @param jwt    The JSON Web Token that enables password reset.
    * @return       A `Future` containing the message id.
    */
  def sendPasswordResetEmail(user: User, jwt: String): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.passwordReset.subject"),
      views.html.passwordResetEmail(user, jwt)(lang(user.lang))
    )
  }

  /**
    * Sends a verification request approval email to the specified user.
    *
    * @param user   The user to send the approval email to.
    * @return       A `Future` containing the message id.
    */
  def sendVerificationRequestApprovalEmail(user: User): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.verificationRequestApproval.subject"),
      views.html.verificationRequestApprovalEmail(user)(lang(user.lang))
    )
  }

  /**
    * Sends a verification request refusal email to the specified user.
    *
    * @param user   The user to send the refusal email to.
    * @param reason The reason the verification request has been refused.
    * @return       A `Future` containing the message id.
    */
  def sendVerificationRequestRefusalEmail(user: User, reason: String): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.verificationRequestRefusal.subject"),
      views.html.verificationRequestRefusalEmail(user, reason.uncapitalize.stripPunctuation)(lang(user.lang))
    )
  }

  /**
    * Sends an approval revocation email to the specified user.
    *
    * @param user   The user to send the revocation email to.
    * @param reason The reason the approval has been revoked.
    * @return       A `Future` containing the message id.
    */
  def sendApprovalRevocationEmail(user: User, reason: String): Future[String] = {
    emailService.sendEmail(
      None,
      Seq(user.email.get),
      Messages("auth.email.approvalRevocation.subject"),
      views.html.approvalRevocationEmail(user, reason.uncapitalize.stripPunctuation)(lang(user.lang))
    )
  }
}
