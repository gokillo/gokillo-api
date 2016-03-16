/*#
  * @file NotificationDaoComponent.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import scala.concurrent.Future
import services.common.DaoComponent
import models.messaging.Notification

/**
  * Defines functionality for accessing notification data.
  */
trait NotificationDaoComponent extends DaoComponent[Notification] {

  /**
    * Returns an instance of a `NotificationDao` implementation.
    */
  def dao: NotificationDao

  /**
    * Represents a notification data access object.
    */
  trait NotificationDao extends Dao {
  }
}
