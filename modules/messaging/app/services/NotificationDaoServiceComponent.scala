/*#
  * @file NotificationDaoServiceComponent.scala
  * @begin 6-Jul-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import services.common.DefaultDaoServiceComponent
import models.messaging.Notification

/**
  * Implements a `DaoServiceComponent` that provides access to notification data.
  */
trait NotificationDaoServiceComponent extends DefaultDaoServiceComponent[Notification] {
  this: NotificationDaoComponent =>

  /**
    * Returns an instance of a `NotificationDaoService` implementation.
    */
  override def daoService = new NotificationDaoService

  class NotificationDaoService extends DefaultDaoService {
  }
}
