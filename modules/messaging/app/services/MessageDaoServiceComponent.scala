/*#
  * @file MessageDaoServiceComponent.scala
  * @begin 19-Sep-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.messaging

import services.common.DefaultDaoServiceComponent
import models.messaging.Message

/**
  * Implements a `DaoServiceComponent` that provides access to message data.
  */
trait MessageDaoServiceComponent extends DefaultDaoServiceComponent[Message] {
  this: MessageDaoComponent =>

  /**
    * Returns an instance of a `MessageDaoService` implementation.
    */
  override def daoService = new MessageDaoService

  class MessageDaoService extends DefaultDaoService {
  }
}
