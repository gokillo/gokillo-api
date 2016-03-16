/*#
  * @file TokenTraceDaoComponent.scala
  * @begin 26-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent.Future
import services.common.DaoComponent
import models.auth.{Token, TokenTrace}

/**
  * Defines functionality for accessing token trace data.
  */
trait TokenTraceDaoComponent extends DaoComponent[TokenTrace] {

  /**
    * Returns an instance of an `TokenTraceDao` implementation.
    */
  def dao: TokenTraceDao

  /**
    * Represents a token trace data access object.
    */
  trait TokenTraceDao extends Dao {

    /**
      * Finds and updates the token trace associated with the specified token.
      *
      * @param token  The token to update the token trace from.
      * @return       A `Future` value containing the old value of the token
      *               trace associated with `token`, or `None` if it could
      *               not be found.
      */
    def findAndUpdate(token: Token): Future[Option[TokenTrace]]

    /**
      * Removes th thee expired token traces.
      * @return A `Future` value containing the number of token traces removed.
      */
    def removeExpired: Future[Int]
  }
}
