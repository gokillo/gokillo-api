/*#
  * @file MessageDaoComponent.scala
  * @begin 19-Sep-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import scala.concurrent.Future
import services.common.DaoComponent
import models.messaging.Message

/**
  * Defines functionality for accessing message data.
  */
trait MessageDaoComponent extends DaoComponent[Message] {

  /**
    * Returns an instance of a `MessageDao` implementation.
    */
  def dao: MessageDao

  /**
    * Represents a message data access object.
    */
  trait MessageDao extends Dao {
  }
}
