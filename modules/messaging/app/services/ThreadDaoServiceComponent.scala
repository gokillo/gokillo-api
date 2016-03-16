/*#
  * @file ThreadDaoServiceComponent.scala
  * @begin 15-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import services.common.DefaultDaoServiceComponent
import models.common.Id
import models.messaging.Thread

/**
  * Implements a `DaoServiceComponent` that provides access to message thread data.
  */
trait ThreadDaoServiceComponent extends DefaultDaoServiceComponent[Thread] {
  this: ThreadDaoComponent =>

  /**
    * Returns an instance of a `ThreadDaoService` implementation.
    */
  override def daoService = new ThreadDaoService

  class ThreadDaoService extends DefaultDaoService {

    def incMessageCount(threadId: Id, by: Int) = dao.incMessageCount(threadId, by)
    def addGrantees(threadId: Id, grantees: List[String]) = dao.addGrantees(threadId, grantees)
    def removeGrantees(threadId: Id, grantees: List[String]) = dao.removeGrantees(threadId, grantees)
  }
}
