/*#
  * @file ProjectDaoServiceComponent.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import services.common.DefaultDaoServiceComponent
import models.common.Id
import models.core.{Project, FundingInfo, Reward, Faq}

/**
  * Implements a `DaoServiceComponent` that provides access to project data.
  */
trait ProjectDaoServiceComponent extends DefaultDaoServiceComponent[Project] {
  this: ProjectDaoComponent =>

  /**
    * Returns an instance of a `ProjectDaoService` implementation.
    */
  override def daoService = new ProjectDaoService

  class ProjectDaoService extends DefaultDaoService {

    import projects.ProjectFsm._

    def incRaisedAmount(
      projectId: Id, by: Double
    )(implicit state: Ztate) = dao.incRaisedAmount(projectId, by)

    def addReward(
      projectId: Id, reward: Reward
    )(implicit state: Ztate) = dao.addReward(projectId, reward)

    def updateReward(
      projectId: Id, index: Int, reward: Reward
    )(implicit state: Ztate) = dao.updateReward(projectId, index, reward)

    def updateRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double, reward: Reward
    )(implicit state: Ztate) = dao.updateRewardByPledgeAmount(projectId, pledgeAmount, reward)

    def removeReward(
      projectId: Id, index: Int
    )(implicit state: Ztate) = dao.removeReward(projectId, index)

    def removeRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate) = dao.removeRewardByPledgeAmount(projectId, pledgeAmount)

    def findReward(
      projectId: Id, index: Int
    )(implicit state: Ztate) = dao.findReward(projectId, index)

    def findRewardById(
      projectId: Id, rewardId: Id
    )(implicit state: Ztate) = dao.findRewardById(projectId, rewardId)

    def findRewards(
      projectId: Id
    )(implicit state: Ztate) = dao.findRewards(projectId)

    def selectRewardByPledgeAmount(
      projectId: Id, pledgeAmount: Double
    )(implicit state: Ztate) = dao.selectRewardByPledgeAmount(projectId, pledgeAmount)

    def addFaq(
      projectId: Id, faq: Faq
    )(implicit state: Ztate) = dao.addFaq(projectId, faq)

    def updateFaq(
      projectId: Id, index: Int, faq: Faq
    )(implicit state: Ztate) = dao.updateFaq(projectId, index, faq)

    def removeFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate) = dao.removeFaq(projectId, index)

    def findFaq(
      projectId: Id, index: Int
    )(implicit state: Ztate) = dao.findFaq(projectId, index)

    def findFaqs(
      projectId: Id
    )(implicit state: Ztate) = dao.findFaqs(projectId)
  }
}
