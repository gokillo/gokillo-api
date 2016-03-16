/*#
  * @file EmailHelper.scala
  * @begin 30-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.core

import scala.concurrent.Future
import play.api.i18n.Messages
import utils.common.EmailHelperBase
import utils.common.typeExtensions._
import models.auth.User
import models.core.Project
import models.pay.Coin

/**
  * Provides functionality for sending emails about projects.
  */
object EmailHelper extends EmailHelperBase {

  /**
    * Emails addressed to originators.
    */
  object originator {

    /**
      * Sends a ''publishing'' confirmation email to the specified originator.
      *
      * @param originator The originator to send the confirmation email to.
      * @param project    The project that has been published.
      * @return           A `Future` containing the message id.
      */
    def sendPublishingConfirmationEmail(originator: User, project: Project): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.publishingConfirmation.subject", project.name.get),
        views.html.originator.publishingConfirmationEmail(originator, project)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''project rejected'' notification email to the specified originator.
      *
      * @param originator The originator to send the notification email to.
      * @param project    The project that has been rejected.
      * @param reason     The reason the project has been rejected.
      * @return           A `Future` containing the message id.
      */
    def sendProjectRejectedNotificationEmail(originator: User, project: Project, reason: String): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.projectRejectedNotification.subject", project.name.get),
        views.html.originator.projectRejectedNotificationEmail(originator, project, reason.uncapitalize.stripPunctuation)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''target hit'' notification email to the specified originator.
      *
      * @param originator The originator to send the notification email to.
      * @param project    The project that reached the funding target.
      * @param cashInPeriod The amount of time, in minutes, cash-in is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendTargetHitNotificationEmail(originator: User, project: Project, cashInPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.targetHitNotification.subject", project.name.get),
        views.html.originator.targetHitNotificationEmail(originator, project, cashInPeriod)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''granted by score'' notification email to the specified originator.
      *
      * @param originator The originator to send the notification email to.
      * @param project    The project considered eligible for funding.
      * @param cashInPeriod The amount of time, in minutes, cash-in is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendGrantedByScoreNotificationEmail(originator: User, project: Project, cashInPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.grantedByScoreNotification.subject", project.name.get),
        views.html.originator.grantedByScoreNotificationEmail(originator, project, cashInPeriod)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''target missed'' notification email to the specified originator.
      *
      * @param originator The originator to send the notification email to.
      * @param project    The project that did not reach the funding target.
      * @return           A `Future` containing the message id.
      */
    def sendTargetMissedNotificationEmail(originator: User, project: Project): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.targetMissedNotification.subject", project.name.get),
        views.html.originator.targetMissedNotificationEmail(originator, project)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''funding'' confirmation email to the specified originator.
      *
      * @param originator The originator to send the confirmation email to.
      * @param project    The project that has been funded.
      * @param amount     The funding amount.
      * @return           A `Future` containing the message id.
      */
    def sendFundingConfirmationEmail(originator: User, project: Project, amount: Coin): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.fundingConfirmation.subject", project.name.get),
        views.html.originator.fundingConfirmationEmail(originator, project, amount)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''cash-in period expired'' notification email to the specified
      * originator.
      *
      * @param originator The originator to send the notification email to.
      * @param project    The project for which the cash-in period has expired.
      * @return           A `Future` containing the message id.
      */
    def sendCashInPeriodExpiredNotificationEmail(originator: User, project: Project): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.cashInPeriodExpiredNotification.subject", project.name.get),
        views.html.originator.cashInPeriodExpiredNotificationEmail(originator, project)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''received pledge'' notification email to the specified originator.
      *
      * @param originator The originator to send the notification email to.
      * @param backer     The backer that pledged to the project.
      * @param project    The project the backer pledged to.
      * @param amount     The amount pledged.
      * @return           A `Future` containing the message id.
      */
    def sendReceivedPledgeNotificationEmail(originator: User, backer: User, project: Project, amount: Coin): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.receivedPledgeNotification.subject", project.name.get),
        views.html.originator.receivedPledgeNotificationEmail(originator, backer, project, amount)(lang(originator.lang))
      )
    }

    /**
      * Sends a ''cash-in'' reminder email to the specified originator.
      *
      * @param originator The originator to send the reminder email to.
      * @param project    The project ready to be funded.
      * @param cashInPeriod The amount of time, in minutes, cash-in is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendCashInReminderEmail(originator: User, project: Project, cashInPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(originator.email.get),
        Messages("core.email.originator.cashInReminder.subject", project.name.get),
        views.html.originator.cashInReminderEmail(originator, project, cashInPeriod)(lang(originator.lang))
      )
    }
  }

  /**
    * Emails addressed to backers.
    */
  object backer {

    /**
      * Sends a ''target hit'' notification email to the specified backer.
      *
      * @param backer     The backer to send the notification email to.
      * @param originator The owner of the project.
      * @param project    The project that reached the funding target.
      * @param cashInPeriod The amount of time, in minutes, cash-in is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendTargetHitNotificationEmail(backer: User, originator: User, project: Project, cashInPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.targetHitNotification.subject", project.name.get),
        views.html.backer.targetHitNotificationEmail(backer, originator, project, cashInPeriod)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''granted by score'' notification email to the specified backer.
      *
      * @param backer     The backer to send the notification email to.
      * @param originator The owner of the project.
      * @param project    The project considered eligible for funding.
      * @param cashInPeriod The amount of time, in minutes, cash-in is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendGrantedByScoreNotificationEmail(backer: User, originator: User, project: Project, cashInPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.grantedByScoreNotification.subject", project.name.get),
        views.html.backer.grantedByScoreNotificationEmail(backer, originator, project, cashInPeriod)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''target missed'' notification email to the specified backer.
      *
      * @param backer     The backer to send the notification email to.
      * @param project    The project that did not reach the funding target.
      * @param refundPeriod The amount of time, in minutes, refund is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendTargetMissedNotificationEmail(backer: User, project: Project, refundPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.targetMissedNotification.subject", project.name.get),
        views.html.backer.targetMissedNotificationEmail(backer, project, refundPeriod)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''funding'' confirmation email to the specified backer.
      *
      * @param backer     The backer to send the confirmation email to.
      * @param originator The owner of the project.
      * @param project    The project that has been funded.
      * @return           A `Future` containing the message id.
      */
    def sendFundingConfirmationEmail(backer: User, originator: User, project: Project): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.fundingConfirmation.subject", project.name.get),
        views.html.backer.fundingConfirmationEmail(backer, originator, project)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''refund'' confirmation email to the specified backer.
      *
      * @param backer     The backer to send the confirmation email to.
      * @param project    The project the pledge has been refunded for.
      * @param amount     The refund amount.
      * @return           A `Future` containing the message id.
      */
    def sendRefundConfirmationEmail(backer: User, project: Project, amount: Coin): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.refundConfirmation.subject", project.name.get),
        views.html.backer.refundConfirmationEmail(backer, project, amount)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''cash-in period expired'' notification email to the specified
      * backer.
      *
      * @param backer     The backer to send the notification email to.
      * @param originator The owner of the project.
      * @param project    The project for which the cash-in period has expired.
      * @param refundPeriod The amount of time, in minutes, refund is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendCashInPeriodExpiredNotificationEmail(backer: User, originator: User, project: Project, refundPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.cashInPeriodExpiredNotification.subject", project.name.get),
        views.html.backer.cashInPeriodExpiredNotificationEmail(backer, originator, project, refundPeriod)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''pledge received'' notification email to the specified backer.
      *
      * @param backer     The backer to send the notification email to.
      * @param originator The owner of the project.
      * @param project    The project the backer pledged to.
      * @param amount     The amount pledged.
      * @return           A `Future` containing the message id.
      */
    def sendPledgeReceivedNotificationEmail(backer: User, originator: User, project: Project, amount: Coin): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.pledgeReceivedNotification.subject", project.name.get),
        views.html.backer.pledgeReceivedNotificationEmail(backer, originator, project, amount)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''refund'' reminder email to the specified backer.
      *
      * @param backer     The backer to send the reminder email to.
      * @param project    The project that is not being funded.
      * @param refundPeriod The amount of time, in minutes, refund is allowed.
      * @return           A `Future` containing the message id.
      */
    def sendRefundReminderEmail(backer: User, project: Project, refundPeriod: Int): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.refundReminder.subject", project.name.get),
        views.html.backer.refundReminderEmail(backer, project, refundPeriod)(lang(backer.lang))
      )
    }

    /**
      * Sends a ''refund period expired'' notification email to the specified
      * backer.
      *
      * @param backer   The backer to send the notification email to.
      * @param project  The project for which the refund period has expired.
      * @return         A `Future` containing the message id.
      */
    def sendRefundPeriodExpiredNotificationEmail(backer: User, project: Project): Future[String] = {
      emailService.sendEmail(
        None,
        Seq(backer.email.get),
        Messages("core.email.backer.refundPeriodExpiredNotification.subject", project.name.get),
        views.html.backer.refundPeriodExpiredNotificationEmail(backer, project)(lang(backer.lang))
      )
    }
  }
}
