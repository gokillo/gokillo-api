/*#
  * @file AuthPlugin.scala
  * @begin 30-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent.Future
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import play.api.{Application, Plugin}
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import models.auth._

/**
  * Implements the `auth` plugin.
  *
  * @constructor  Initializes a new instance of the `AuthPlugin` class.
  * @param app    The current application.
  */
class AuthPlugin(app: Application) extends Plugin {

  import scala.concurrent.duration._
  import scala.util.control.Breaks._
  import akka.actor.Cancellable
  import utils.common.PeerType
  import utils.common.PeerType._
  import utils.common.env._
  import AuthPlugin._

  private val ApiKeyRenewInterval = configuration.getInt("auth.apiKeyRenewInterval").getOrElse(15)
  private val TokenDeleteInterval = configuration.getInt("auth.tokenDeleteInterval").getOrElse(5)
  private var cancellableDiscardExpiredTokens: Option[Cancellable] = None
  private var cancellableRenewApiKeys: Option[Cancellable] = None

  /**
    * Called when the application starts
    */
  override def onStart = {
    def _discardExpiredTokens: Future[Unit] = {
      discardExpiredTokens.map { _ => cancellableDiscardExpiredTokens = Some(
        Akka.system.scheduler.scheduleOnce(TokenDeleteInterval.minutes)(_discardExpiredTokens)
      )}
    }

    def _renewApiKeys: Future[Unit] = {
      renewApiKeys.map { _ => cancellableRenewApiKeys = Some(
        Akka.system.scheduler.scheduleOnce(ApiKeyRenewInterval.minutes)(_renewApiKeys)
      )}
    }

    ensureDefaultSuperuser.map { _.foreach { _.metaAccounts.foreach { accounts =>
      breakable {
        accounts.foreach { account => if (account.default) {
          peers.values.filter(
            peer => peer.peerType == NativeApiConsumer || peer.peerType == ForeignApiConsumer
          ).map(
            peer => (peer.name, peer.description, account, peer.peerType == NativeApiConsumer)
          ).foldLeft(Future(List.empty[Any]))((future, params) => {
            for {
              prev <- future
              curr <- (ensureApiConsumer _).tupled(params)
            } yield prev :+ curr
          })
          break
        }}
      }
    }}}

    if (cacheTokens) {
      Logger.info("Token store set to public cache")
    } else {
      cancellableDiscardExpiredTokens = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_discardExpiredTokens))
    }

    cancellableRenewApiKeys = Some(Akka.system.scheduler.scheduleOnce(0.seconds)(_renewApiKeys))

    Logger.info("AuthPlugin started")
  }

  /**
    * Called when the application stops.
    */
  override def onStop = {
    cancellableDiscardExpiredTokens.foreach(_.cancel)
    cancellableRenewApiKeys.foreach(_.cancel)

    Logger.info("AuthPlugin stopped")
  }
}

object AuthPlugin {

  import scala.util.control.NonFatal
  import scala.concurrent.Future
  import scala.util.{Success, Failure}
  import play.api.libs.json._
  import play.api.mvc.RequestHeader
  import brix.crypto.Secret
  import utils.common.Formats._
  import utils.auth.EmailHelper
  import models.common.{Id, State}
  import models.auth.Role
  import models.auth.TokenType._

  import mongo._
  import cache._
  import users.fsm
  import users.fsm.Approved

  final val ApiKeyLength = 512
  final val DefaultSuperuser = "admin"
  val cacheTokens = configuration.getBoolean("auth.cacheTokens").getOrElse(false)

  private val apiConsumerService: ApiConsumerDaoServiceComponent#ApiConsumerDaoService = new ApiConsumerDaoServiceComponent
    with MongoApiConsumerDaoComponent {
  }.daoService.asInstanceOf[ApiConsumerDaoServiceComponent#ApiConsumerDaoService]

  private val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
    with MongoUserDaoComponent {
  }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

  private val accountService: AccountDaoServiceComponent#AccountDaoService = new AccountDaoServiceComponent
    with MongoAccountDaoComponent {
  }.daoService.asInstanceOf[AccountDaoServiceComponent#AccountDaoService]

  private val tokenTraceService: TokenTraceDaoServiceComponent#TokenTraceDaoService = (
    if (cacheTokens)
      new TokenTraceDaoServiceComponent with CacheTokenTraceDaoComponent {}
    else
      new TokenTraceDaoServiceComponent with MongoTokenTraceDaoComponent {}
  ).daoService.asInstanceOf[TokenTraceDaoServiceComponent#TokenTraceDaoService]

  /**
    * Verifies whether the default superuser already exists and in case creates it.
    *
    * @return An `Option` value containing the default superuser, or `None` if it
    *         could not be found or created.
    */
  private def ensureDefaultSuperuser: Future[Option[User]] = {
    configuration.getString("auth.superuser.email").fold {
      Logger.warn("could not create default superuser: email address not defined")
      Future.successful(None.asInstanceOf[Option[User]])
    } { email => {
      val username = configuration.getString("auth.superuser.username") orElse Some(DefaultSuperuser)
      val superuser = User(
        email = Some(email),
        username = username,
        company = configuration.getString("common.company.name"),
        state = Some(State(Approved)),
        metaAccounts =  Some(List(MetaAccount(
          Some(accountService.generateId),
          username,
          None,
          Some(true)
        )))
      )

      userService.find(
        Id("username", username),
        Some(Json.obj("$exclude" -> Json.arr("password")))
      ).flatMap {
        case None =>
          userService.insert(superuser).flatMap { newSuperuser =>
            Logger.debug(s"created default superuser ${newSuperuser.id.get}")
            superuser.id = newSuperuser.id
            accountService.insert(Account(
              id = newSuperuser.metaAccounts.get(0).id,
              ownerId = newSuperuser.id,
              roles = Some(List(Role.Superuser.id))
            ))
          }.map { newAccount =>
            userService.activateAccount(newAccount.ownerId.get, newAccount.id.get).map { _ =>
              createToken(Reset, None, Some(newAccount), username).map { token =>
                EmailHelper.sendPasswordResetEmail(superuser, token.asJwt)
              }.recover { case e =>
                Logger.error("error creating reset token for default superuser", e)
              }
            }
            Some(superuser)
          }
        case some => Future.successful(some) // default superuser already exists
      }.recover { case e =>
        Logger.error("error ensuring default superuser", e)
        None
      }
    }}
  }

  /**
    * Verifies whether the specified API consumer already exists and in case creates it.
    *
    * @param name     The name of the API consumer.
    * @param description The description of the API consumer.
    * @param owner    The account that owns the API consumer identified by `name`.
    * @param native   A Boolean value indicating whether or not the API consumer is native.
    * @return         An `Option` value containing the API consumer identified by `name`,
    *                 or `None` if it could not be found or created.
    */
  private def ensureApiConsumer(
    name: String, description: String, owner: MetaAccount, native: Boolean
  ): Future[Option[ApiConsumer]] = {
    apiConsumerService.find(
      Json.obj("name" -> name),
      Some(Json.obj("$exclude" -> Json.arr("apiKey"))),
      None,
      0, 1
    ).flatMap { _.headOption match {
      case None =>
        accountService.insert(Account(
          ownerId = Some(apiConsumerService.generateId),
          roles = Some(List(Role.Guest.id))
        )).flatMap { newAccount =>
          val apiConsumer = ApiConsumer(
            id = newAccount.ownerId,
            accountId = newAccount.id,
            ownerId = owner.id,
            name = Some(name),
            description = Some(description),
            apiKey = Some(Secret(ApiKeyLength).value),
            native = if (native) Some(native) else None
          )
          apiConsumerService.insert(apiConsumer).map { newApiConsumer =>
            Logger.debug(s"created API consumer $name with id ${newApiConsumer.id.get}")
            Some(newApiConsumer)
          }
        }
      case some => Future.successful(some) // api consumer already exists
    }}.recover { case e =>
      Logger.error(s"error creating api consumer $name", e)
      None
    }
  }

  /**
    * Generates an unique token id.
    * @return An unique token id.
    */
  def generateTokenId: String = tokenTraceService.generateId

  /**
    * Creates a new `Token` for the specified account.
    *
    * @param tokenType  One of the `TokenType` values.
    * @param apiKey     The secret API key used to sign HTTP requests.
    * @param username   The username of the subject.
    * @param extendable A Boolean value indicating whether or not the token
    *                   duration is extendable. Default to `true`.
    * @param request    The current HTTP request header, if any.
    * @return           A `Future` value containing a new token of type `tokenType`.
    */
  def createToken(
    tokenType: TokenType,
    apiKey: Option[String],
    account: Option[Account],
    username: Option[String] = None,
    extendable: Boolean = true
  )(implicit request: RequestHeader = null): Future[Token] = {
    val token = Token(tokenType, apiKey, account, username, None, extendable)
    tokenType match {
      case Browse => Future.successful(token)
      case _ => tokenTraceService.insert(TokenTrace(token.id, username, token.expirationTime)).transform(
        success => token,
        failure => failure
      )
    }
  }

  /**
    * Deserializes a `Token` from the specified JSON Web Token.
    *
    * @param jwt  The JSON Web Token to deserialize a `Token` from.
    * @return     A `Future` value containing the deserialized `Token`.
    */
  def token(jwt: String): Future[Token] = Future(
    Token(jwt)
  ).flatMap { token => token.tokenType match {
    case Browse => Future.successful(token)
    case _ =>
      if (token.tokenType == Authorization) token.extendDuration
      tokenTraceService.findAndUpdate(token).map {
        case None => token.expire; token
        case _ => token
      }
  }}

  /**
    * Discards the token identified by the specified id.
    *
    * @param tokenId  The id of the token to discard.
    * @return         A `Future` value containing either `true` if the token
    *                 identified by `tokenId` was discarded or `false` otherwise.
    */
  def discardToken(tokenId: String): Future[Boolean] = tokenTraceService.remove(tokenId)

  /**
    * Discards the expired tokens.
    */
  def discardExpiredTokens: Future[Unit] = {
    tokenTraceService.removeExpired.recover {
      case NonFatal(e) => Logger.error("error discarding expired tokens", e)
    }.map { _ => }
  }

  /**
    * Renews the secret keys of native API consumers.
    * @note Native API consumers are those that do not come from third parties.
    */
  def renewApiKeys: Future[Unit] = {
    apiConsumerService.find(
      Json.obj("native" -> true),
      Some(Json.obj("$include" -> Json.arr("id"))),
      None,
      0, Int.MaxValue
    ).map { _.foreach { apiConsumer =>
      apiConsumerService.updateApiKey(Id(apiConsumer.id), Secret(ApiKeyLength).value).map {
        case Some(_) => Logger.debug(s"renewed secret key for native api consumer ${apiConsumer.id.get}")
        case _ => Logger.warn(
            s"could not renew secret key for native api consumer ${apiConsumer.id.get}: " +
            "api consumer no longer available"
          )
      }.recover { case NonFatal(e) =>
        Logger.error(s"error renewing secret key for native api consumer ${apiConsumer.id.get}", e)
      }
    }}.recover { case NonFatal(e) =>
      Logger.error(s"error retrieving native api consumers to renew secret keys for", e)
    }
  }
}
