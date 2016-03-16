/*#
  * @file ApiConsumerDaoServiceComponent.scala
  * @begin 31-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.DefaultDaoServiceComponent
import models.common.Id
import models.auth.ApiConsumer

/**
  * Implements a `DaoServiceComponent` that provides access to API consumer data.
  */
trait ApiConsumerDaoServiceComponent extends DefaultDaoServiceComponent[ApiConsumer] {
  this: ApiConsumerDaoComponent =>

  /**
    * Returns an instance of an `ApiConsumerDaoService` implementation.
    */
  override def daoService = new ApiConsumerDaoService

  class ApiConsumerDaoService extends DefaultDaoService {

    def updateApiKey(apiConsumerId: Id, apiKey: String) = dao.updateApiKey(apiConsumerId, apiKey)
  }
}
