/*#
  * @file ThreadDaoComponent.scala
  * @begin 15-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import scala.concurrent.Future
import services.common.DaoComponent
import models.common.Id
import models.messaging.Thread

/**
  * Defines functionality for accessing message thread data.
  */
trait ThreadDaoComponent extends DaoComponent[Thread] {

  /**
    * Returns an instance of a `ThreadDao` implementation.
    */
  def dao: ThreadDao

  /**
    * Represents a message thread data access object.
    */
  trait ThreadDao extends Dao {

    /**
      * Increments the number of messages in the thread identified by the specified id.
      *
      * @param threadId The id that identifies the thread to increment the message count for.
      * @param by       The value by which to increment the message count.
      * @return         A `Future` value containing the previous message count, or `None` if the
      *                 thread identified by `threadId` could not be found.
      */
    def incMessageCount(threadId: Id, by: Int): Future[Option[Int]]

    /**
      * Adds the specified grantees to the thread identified by the specified id.
      *
      * @param threadId The id that identifies the thread to add the grantees to.
      * @param grantees The grantees to add.
      */
    def addGrantees(threadId: Id, grantees: List[String]): Future[Unit]

    /**
      * Removes the specified grantees from the thread identified by the specified id.
      *
      * @param threadId The id that identifies the thread to remove the grantees from.
      * @param grantees The grantees to remove.
      */
    def removeGrantees(threadId: Id, grantees: List[String]): Future[Unit]
  }
}
