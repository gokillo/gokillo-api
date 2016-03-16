/*#
  * @file TokenTraceDaoServiceComponent.scala
  * @begin 26-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.DefaultDaoServiceComponent
import models.auth.{Token, TokenTrace}

/**
  * Implements a `DaoServiceComponent` that provides access to token trace data.
  */
trait TokenTraceDaoServiceComponent extends DefaultDaoServiceComponent[TokenTrace] {
  this: TokenTraceDaoComponent =>

  /**
    * Returns an instance of an `TokenTraceDaoService` implementation.
    */
  override def daoService = new TokenTraceDaoService

  class TokenTraceDaoService extends DefaultDaoService {

    def findAndUpdate(token: Token) = dao.findAndUpdate(token)
    def removeExpired = dao.removeExpired
  }
}
