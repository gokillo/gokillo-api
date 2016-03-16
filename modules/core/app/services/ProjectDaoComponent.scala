/*#
  * @file ProjectDaoComponent.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import scala.concurrent.Future
import services.common.DaoComponent
import models.common.Id
import models.core.{Project, FundingInfo, Reward, Faq}

/**
  * Defines functionality for accessing project data.
  */
trait ProjectDaoComponent extends DaoComponent[Project] {

  /**
    * Returns an instance of a `ProjectDao` implementation.
    */
  def dao: ProjectDao

  /**
    * Represents a project data access object.
    */
  trait ProjectDao extends Dao {

    import projects.ProjectFsm._

    /**
      * Increments the raised amount of the project identified by the specified
      * id.
      *
      * @param projectId  The id that identifies the project to increment the raided amount for.
      * @param by         The value by which to increment the raised amount.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the old `FundingInfo`, or `None` if the
      *                   project identified by `projectId` could not be found.
      */
    def incRaisedAmount(
      projectId: Id, by: Double
    )(implicit state: Ztate): Future[Option[FundingInfo]]

    /**
      * Adds the specified reward to the project identified by the specified
      * id.
      *
      * @param projectId  The id that identifies the project to add the reward to.
      * @param reward     The reward to add.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the zero-based index of the added
      *                   reward, or `None` if the project identified by `projectId` could
      *                   not be found.
      */
    def addReward(
      projectId: Id, reward: Reward
    )(implicit state: Ztate): Future[Option[Int]]

    /**
      * Updates the reward at the specified index of the project identified
      * by the specified id.
      *
      * @param projectId  The id that identifies the project to update the reward for.
      * @param index      The index of the reward to update.
      * @param reward     The reward update.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the old value of the reward
      *                   at `index`, or `None` if the project or the reward could
      *                   not be found.
      */
    def updateReward(
      projectId: Id, index: Int, reward: Reward
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Updates the reward with the specified pledge amount of the project identified
      * by the specified id.
      *
      * @param projectId    The id that identifies the project to update the reward for.
      * @param pledgeAmount The pledge amount of the reward to update.
      * @param reward       The reward update.
      * @param state        The current state of the FSM.
      * @return             A `Future` value containing the old value of the reward
      *                     with pledge amount `pledgeAmount`, or `None` if the project
      *                     or the reward could not be found.
      */
    def updateRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double, reward: Reward
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Removes the reward at the specified index of the project identified
      * by the specified id.
      *
      * @param projectId  The id that identifies the project to remove the reward for.
      * @param index      The index of the reward to remove.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the removed reward of the project
      *                   identified by `projectId`, or `None` if the project or the
      *                   reward could not be found.
      */
    def removeReward(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Removes the reward with the specified pledge amount of the project identified
      * by the specified id.
      *
      * @param projectId    The id that identifies the project to remove the reward for.
      * @param pledgeAmount The pledge amount of the reward to remove.
      * @param state        The current state of the FSM.
      * @return             A `Future` value containing the removed reward of the
      *                     project identified by `projectId`, or `None` if the project
      *                     or the reward could not be found.
      */
    def removeRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Finds the reward at the specified index of the project identified
      * by the specified id.
      *
      * @param projectId  The id that identifies the project to find the reward for.
      * @param index      The index of the reward to find.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the reward at `index` of the
      *                   project identified by `projectId`, or `None` if the project
      *                   or the reward could not be found.
      */
    def findReward(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Finds the reward identified by the specified id.
      *
      * @param projectId  The id that identifies the project to find the reward for.
      * @param rewardId   The id of the reward to find.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the reward identified by `rewardId`,
      *                   or `None` if the project or the reward could not be found.
      */
    def findRewardById(
      projectId: Id, rewardId: Id
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Finds the rewards of the project identified by the specified id.
      *
      * @param projectId  The id that identifies the project to find the rewards for.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the rewards of the project
      *                   identified by `projectId`, or an empty `Seq` if the project
      *                   could not be found or no rewards could be found.
      */
    def findRewards(
      projectId: Id
    )(implicit state: Ztate): Future[Seq[Reward]]

    /**
      * Selects the reward for the specified pledge amount from the project identified
      * by the specified id.
      *
      * @param projectId    The id that identifies the project to select the reward from.
      * @param pledgeAmount The pledge amount to select the reward for.
      * @param state        The current state of the FSM.
      * @return             A `Future` value containing the reward selected for `pledgeAmount`,
      *                     or `None` if the project or the reward could not be found.
      */
    def selectRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate): Future[Option[Reward]]

    /**
      * Adds the specified faq to the project identified by the specified id.
      *
      * @param projectId  The id that identifies the project to add the faq to.
      * @param faq        The faq to add.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the zero-based index of the added
      *                   faq, or `None` if the project identified by `projectId` could
      *                   not be found.
      */
    def addFaq(
      projectId: Id, faq: Faq
    )(implicit state: Ztate): Future[Option[Int]]

    /**
      * Updates the faq at the specified index of the project identified by the
      * specified id.
      *
      * @param projectId  The id that identifies the project to update the faq for.
      * @param index      The index of the faq to update.
      * @param faq        The faq update.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the old value of the faq
      *                   at `index`, or `None` if the project or the faq could
      *                   not be found.
      */
    def updateFaq(
      projectId: Id, index: Int, faq: Faq
    )(implicit state: Ztate): Future[Option[Faq]]

    /**
      * Removes the faq at the specified index of the project identified by the
      * specified id.
      *
      * @param projectId  The id that identifies the project to remove the faq for.
      * @param index      The index of the faq to remove.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the removed faq of the project
      *                   identified by `projectId`, or `None` if the project or the
      *                   faq could not be found.
      */
    def removeFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Faq]]

    /**
      * Finds the faq at the specified index of the project identified by the
      * specified id.
      *
      * @param projectId  The id that identifies the project to find the faq for.
      * @param index      The index of the faq to find.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the faq at `index` of the
      *                   project identified by `projectId`, or `None` if the project
      *                   or the faq could not be found.
      */
    def findFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate): Future[Option[Faq]]

    /**
      * Finds the faqs of the project identified by the specified id.
      *
      * @param projectId  The id that identifies the project to find the faqs for.
      * @param state      The current state of the FSM.
      * @return           A `Future` value containing the faqs of the project
      *                   identified by `projectId`, or an empty `Seq` if the project
      *                   could not be found.
      */
    def findFaqs(
      projectId: Id
    )(implicit state: Ztate): Future[Seq[Faq]]
  }
}
