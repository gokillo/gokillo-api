/*#
  * @file ApiConsumerDaoComponent.scala
  * @begin 31-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent.Future
import services.common.DaoComponent
import models.common.Id
import models.auth.ApiConsumer

/**
  * Defines functionality for accessing API consumer data.
  */
trait ApiConsumerDaoComponent extends DaoComponent[ApiConsumer] {

  /**
    * Returns an instance of an `ApiConsumerDao` implementation.
    */
  def dao: ApiConsumerDao

  /**
    * Represents an API consumer data access object.
    */
  trait ApiConsumerDao extends Dao {

    /**
      * Updates the secret key of the API consumer identified by the specified id.
      *
      * @param apiConsumerId  The id that identifies the API consumer to update the
      *                       secret key for.
      * @return               A `Future` value containing the old secret key or
      *                       `None` if the API consumer could not be found.
      */
    def updateApiKey(apiConsumerId: Id, apiKey: String): Future[Option[String]]
  }
}
