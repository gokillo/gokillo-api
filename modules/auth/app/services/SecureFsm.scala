/*#
  * @file SecureFsm.scala
  * @begin 15-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import services.common.FsmBase
import models.auth.Token

/**
  * Implements a secure finite state machine (FSM).
  */
trait SecureFsm extends FsmBase {

  /**
    * Holds the current state and provides functionality for transducing to other states
    * with security enabled.
    *
    * @constructor        Initializes a new instance of the [[SecureFsm#Val]] class.
    * @param name         The name of the state.
    * @param convertWith  The `FsmTransduction` that actually converts the current state.
    */
  protected abstract class Val(name: String, convertWith: FsmTransduction) extends super.Val(name, convertWith) {

    /** Enables security when transduciong the current state. */
    def withSecurity(token: Token) = { convertWith.asInstanceOf[FsmTransduction]token = Some(token); this }
  }

  trait SecureFsmTransduction extends FsmTransduction {

    import scala.concurrent.Future
    import scala.collection.mutable.Map
    import services.auth.AuthErrors
    import utils.common.typeExtensions._
    import models.auth.Role._

    /** Message driven security implementation. */
    protected val authorizeImpl: Map[String, (String, Token) => Boolean]

    /**
      * Authorizes the operation triggered by the specified message if the account
      * identified by the specified id has the required privileges.
      * 
      * @tparam T           The type of the value returned by `f`.
      * @param message      The message that triggers the operation to authorize.
      * @param accountId    The identifier of the account for which to authorize `f`.
      * @param f            The function that actually implements the operation to authorize.
      * @return             A `Future` value containing an instance of `T`.
      * @note               If security is not enabled, `f` is always authorized.
      */
    protected def authorize[T](message: FsmMessage, accountId: String, f: () => Future[T]): Future[T] = {
      import scala.collection.immutable.Map
      import models.auth.TokenType._

      this.token.asInstanceOf[Option[Token]].map { token =>
        token.roles.contains(Superuser.id) || authorizeImpl(message.name)(accountId, token) match {
          case true => f()
          case false => Future.failed(AuthErrors.NotAuthorized("operation", message.name, "user", token.username))
        }
      } getOrElse f()
    }
  }
}
