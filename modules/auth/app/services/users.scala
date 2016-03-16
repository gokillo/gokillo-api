/*#
  * @file users.scala
  * @begin 8-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth

import scala.concurrent._
import scala.concurrent.duration._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.libs.json.extensions._
import utils.common.typeExtensions._
import services.common.CommonErrors._
import services.common.DaoErrors._
import services.common.FsmErrors._
import services.auth.AuthErrors._
import models.common.{Id, HistoryEvent, MetaFile, State}
import models.auth.{Token, ProofOf, User}
import models.auth.ProofOf._
import models.auth.Role._
import mongo._

package object users {

  /** Implicit FSM used by JSON state serializer/deserializer. */
  implicit val fsm = UserFsm

  /**
    * Implements a finite state machine (FSM) for transducing user states.
    */
  object UserFsm extends SecureFsm {

    sealed trait Message extends FsmMessage { def userId: String }

    case class Save(user: User) extends Message {
      def userId: String = throw new UnsupportedOperationException
    }
    case class RequestVerification(userId: String) extends Message
    case class AcquireForVerification(userId: String) extends Message
    case class ApproveVerificationRequest(userId: String) extends Message
    case class RefuseVerificationRequest(userId: String, reason: String) extends Message
    case class RevokeApproval(userId: String, reason: String) extends Message
    case class List(
      selector: Option[JsValue], projection: Option[JsValue],
      sort: Option[JsValue], page: Int, perPage: Int
    ) extends Message {
      def userId: String = throw new UnsupportedOperationException
    }
    case class AddProof(userId: String, proof: MetaFile, proofOf: ProofOf, page: Int = 0) extends Message
    case class SetProofOf(userId: String, proofId: String, proofOf: ProofOf) extends Message
    case class DeleteProof(userId: String, proofOf: ProofOf) extends Message
    case class FindProof(userId: String, proofOf: ProofOf, page: Int = 0) extends Message
    case class ListProofs(userId: String) extends Message

    private[UserFsm] class Val(name: String, convertWith: FsmTransduction) extends super.Val(name, convertWith) {

      def !(message: Save) = convertWith.asInstanceOf[Transduction](message)
      def !(message: RequestVerification) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AcquireForVerification) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ApproveVerificationRequest) = convertWith.asInstanceOf[Transduction](message)
      def !(message: RefuseVerificationRequest) = convertWith.asInstanceOf[Transduction](message)
      def !(message: RevokeApproval) = convertWith.asInstanceOf[Transduction](message)
      def !(message: List) = convertWith.asInstanceOf[Transduction](message)
      def !(message: AddProof) = convertWith.asInstanceOf[Transduction](message)
      def !(message: SetProofOf) = convertWith.asInstanceOf[Transduction](message)
      def !(message: DeleteProof) = convertWith.asInstanceOf[Transduction](message)
      def !(message: FindProof) = convertWith.asInstanceOf[Transduction](message)
      def !(message: ListProofs) = convertWith.asInstanceOf[Transduction](message)
    }

    protected def Val(name: String, convertWith: FsmTransduction) = new Val(name, convertWith)

    private val userService: UserDaoServiceComponent#UserDaoService = new UserDaoServiceComponent
      with MongoUserDaoComponent {
    }.daoService.asInstanceOf[UserDaoServiceComponent#UserDaoService]

    /**
      * Implicitly converts the specified state value to a `Val`.
      *
      * @param value  The state value to convert.
      * @return       The `Val` converted from `value`.
      */
    implicit def toVal(value: Value) = value.asInstanceOf[Val]

    /** The user has just been created. */
    val New = Value("new", new FromNew)

    /** The user has been registered without editing privileges. */
    val Registered = Value("registered", new FromRegistered)

    /** The user is awaiting verification. */
    val AwaitingVerification = Value("awaitingVerification", new FromAwaitingVerification)

    /** The user is being verified. */
    val Verification = Value("verification", new FromVerification)

    /** The user has been approved and thus granted editing privileges. */
    val Approved = Value("approved", new FromApproved)

    /**
      * Returns the state of the user identified by the specified id.
      *
      * @param stateOrUserId  The name of the state or the identifier of
      *                       the user to get the state for.
      * @return               The state that matches `stateOrUserId`.
      */
    override def apply(stateOrUserId: String): Ztate = {
      if (!stateOrUserId.isObjectId) super.apply(stateOrUserId)
      else Await.result(userService.find(stateOrUserId), 5 seconds) match {
        case Some(user) => user.state match {
          case Some(state) => state.value match {
            case value if value == Registered.toString => Registered
            case value if value == AwaitingVerification.toString => AwaitingVerification
            case value if value == Verification.toString => Verification
            case value if value == Approved.toString => Approved
            case value => throw NotSupported("state", value)
          }
          case _ => throw InvalidState("user", user.id.get, "undefined")
        }
        case _ => throw NotFound("user", stateOrUserId) // stateOrUserId should contain an id
      }
    }

    /**
      * Provides functionality for converting user states.
      */
    trait Transduction extends SecureFsmTransduction {

      import scala.collection.mutable.{MutableList, Map => MutableMap}
      import scala.collection.immutable.{List => ImmutableList}
      import utils.auth.EmailHelper
      import services.common.{FsServiceComponent, DefaultFsServiceComponent}

      protected implicit val fsService: FsServiceComponent#FsService = new DefaultFsServiceComponent
        with MongoUserFsComponent {
      }.fsService

      protected val accountService: AccountDaoServiceComponent#AccountDaoService = new AccountDaoServiceComponent
        with MongoAccountDaoComponent {
      }.daoService.asInstanceOf[AccountDaoServiceComponent#AccountDaoService]

      protected val authorizeImpl = MutableMap[String, (String, Token) => Boolean](
        "AcquireForVerification" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id)),
        "ApproveVerificationRequest" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id)),
        "RefuseVerificationRequest" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id)),
        "RevokeApproval" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id)),
        "List" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id)),
        "FindProof" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id) || token.accountOwner == userId),
        "ListProofs" -> ((userId: String, token: Token) => token.roles.contains(Auditor.id) || token.accountOwner == userId)
      ).withDefaultValue(
        (userId: String, token: Token) => token.accountOwner == userId
      )

      /**
        * Gets the user identified by the specified id.
        *
        * @param userId The identifier of the user to get.
        * @return       The user identified by `userId`.
        */
      private def getUser(userId: String): Future[Option[User]] = {
        userService.find(
          Json.obj("id" -> userId, "state.value" -> state),
          None, None, 0, 1
        ).map {
          case seq if seq.nonEmpty => seq.headOption
          case _ => None
        }
      }

      /**
        * Sets the state of the specified user.
        *
        * @param user         The user to set the state for.
        * @param state        One of the [[UserFsm]] values.
        * @param historyEvent An `Option` value containing the event description,
        *                     or `None` if unspecified.
        * @return             A `Future` value containing the new state of the user.
        */
      private def setState(
        user: User,
        state: Ztate,
        historyEvent: Option[HistoryEvent] = None
      ): Future[Ztate] = {
        val history = historyEvent.map { event =>
          Json.obj("history" -> (user.asJson.get(__ \ 'history) match {
            case _: JsUndefined => Seq[JsValue](event.asJson)
            case js: JsValue => js.as[JsArray].value :+ event.asJson
          }))
        } getOrElse Json.obj()

        userService.update(
          Json.obj("id" -> user.id, "state.value" -> this.state, "_version" -> user.version),
          Json.obj("state" -> State(state)) ++ history
        ).map {
          case n if n > 0 => state
          case _ => throw StaleObject(user.id.get, userService.collectionName)
        }
      }

      /**
        * Adds the specified roles to the specified user.
        *
        * @param user   The user to add the roles to.
        * @param roles  The roles to add.
        */
      private def addRoles(user: User, roles: ImmutableList[Role]) = {
        userService.findAccounts(user.id).flatMap { _.map { account =>
          (Id(account.id), roles)
        }.foldLeft(Future(ImmutableList.empty[Any]))((future, params) => {
          for {
            prev <- future
            curr <- (accountService.addRoles _).tupled(params)
          } yield prev :+ curr
        })}
      }

      /**
        * Removes the specified roles from the specified user.
        *
        * @param user   The user to remove the roles from.
        * @param roles  The roles to remove.
        */
      private def removeRoles(user: User, roles: ImmutableList[Role]) = {
        userService.findAccounts(user.id).flatMap { _.map { account =>
          (Id(account.id), roles)
        }.foldLeft(Future(ImmutableList.empty[Any]))((future, params) => {
          for {
            prev <- future
            curr <- (accountService.removeRoles _).tupled(params)
          } yield prev :+ curr
        })}
      }

      /**
        * Handles the `Save` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a tuple with the new
        *                 state of the user and the instance saved.
        */
      def apply(message: Save): Future[(Ztate, User)] = {
        val user = User().copy(message.user)
        user.state = Some(State(Registered))
        userService.insert(user).map((Registered, _))
      }

      /**
        * Handles the `RequestVerification` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the user.
        */
      def apply(message: RequestVerification): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          user.addresses match {
            case Some(addresses) if addresses.length > 0 => fsService.find(Json.obj(
                "metadata.userId" -> user.id,
                "metadata.category" -> "control"
              )).flatMap { proofs =>
                var missing = MutableList(Identity, Address)
                user.company.foreach(_ => missing += Incorporation)
                proofs.foreach { _.metadata.foreach { _.getOpt(__ \ 'proofOf).foreach { proofOf =>
                  missing = missing.filterNot(elem => elem == ProofOf(proofOf.as[JsString].value))
                }}}
                if (missing.length == 0) setState(user, AwaitingVerification)
                else Future.failed(MissingProofs(missing.toList.map(_.toString), user.id.get))
              }
            case _ => Future.failed(EmptyList("address", "user", user.username.get))
          }
        )
      }
      
      /**
        * Handles the `AcquireForVerification` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the user.
        */
      def apply(message: AcquireForVerification): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, null, () => setState(user, Verification))
      }

      /**
        * Handles the `ApproveVerificationRequest` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the user.
        */
      def apply(message: ApproveVerificationRequest): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, null, () =>
          addRoles(user, ImmutableList(Editor)).flatMap { _ =>
            setState(
              user, Approved, Some(HistoryEvent("Verification Request Approved"))
            ).map { state =>
              EmailHelper.sendVerificationRequestApprovalEmail(user)
              state
            }
          }
        )
      }

      /**
        * Handles the `RefuseVerificationRequest` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the user.
        */
      def apply(message: RefuseVerificationRequest): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, null, () =>
          removeRoles(user, ImmutableList(Editor)).flatMap { _ =>
            setState(
              user, Registered, Some(HistoryEvent("Verification Request Refused", Some(message.reason)))
            ).map { state =>
              EmailHelper.sendVerificationRequestRefusalEmail(user, message.reason)
              state
            }
          }
        )
      }

      /**
        * Handles the `RevokeApproval` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the new state of the user.
        */
      def apply(message: RevokeApproval): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, null, () =>
          removeRoles(user, ImmutableList(Editor)).flatMap { _ =>
            setState(
              user, Registered, Some(HistoryEvent("Approval Revoked", Some(message.reason)))
            ).map { state =>
              EmailHelper.sendApprovalRevocationEmail(user, message.reason)
              state
            }
          }
        )
      }

      /**
        * Handles the `List` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing a `Seq` of users that match
        *                 the selection criteria specified in `message`, or an empty
        *                 `Seq` if no users could be found in this state.
        * @note           Message `List` does not alter the state of the user.
        */
      def apply(message: List): Future[Seq[User]] = authorize(message, null, () => {
        val selector = Json.obj("state.value" -> state)
        val projection = Json.obj("$exclude" -> Json.arr("password", "public", "addresses", "metaAccounts", "_version"))
          
        userService.find(
          message.selector.map(_.as[JsObject] ++ selector) getOrElse selector,
          message.projection.map(_.as[JsObject] ++ projection) orElse Some(projection),
          message.sort orElse Some(Json.obj("$asc" -> Json.arr("username"))),
          message.page, message.perPage
        )
      })

      /**
        * Handles the `AddProof` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the user.
        * @note           Message `AddProof` does not alter the state of the user.
        */
      def apply(message: AddProof): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          fsService.count(Json.obj(
            "metadata.userId" -> user.id,
            "metadata.category" -> "control",
            "metadata.proofOf" -> message.proofOf
          )).flatMap { count =>
            val page = message.page
            fsService.update(
              message.proof.id,
              Json.obj("metadata" -> Json.obj(
                "userId" -> user.id,
                "category" -> "control",
                "proofOf" -> message.proofOf,
                "page" -> (if (page < 0 || page > count) count else page)
              ))
            )
          }.map { _ => state }
        )
      }

      /**
        * Handles the `SetProofOf` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the user.
        * @note           Message `SetProofOf` does not alter the state of the user.
        */
      def apply(message: SetProofOf): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          fsService.update(
            message.proofId,
            Json.obj("metadata.proofOf" -> message.proofOf)
          ).map { _ => state }
        )
      }

      /**
        * Handles the `DeleteProof` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the state of the user.
        * @note           Message `DeleteProof` does not alter the state of the user.
        */
      def apply(message: DeleteProof): Future[Ztate] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          fsService.remove(Json.obj(
            "metadata.userId" -> user.id,
            "metadata.category" -> "control",
            "metadata.proofOf" -> message.proofOf
          )).map {
            case n if n > 0 => state
            case _ => throw ElementNotFound("proof", message.proofOf.toString, "user", user.username.get) 
          }.map { _ => state }
        )
      }

      /**
        * Handles the `FindProof` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing the proof file of the user,
        *                 or `None` if it could not be found.
        * @note           Message `FindProof` does not alter the state of the user.
        */
      def apply(message: FindProof): Future[Option[MetaFile]] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          fsService.find(Json.obj(
            "metadata.userId" -> user.id,
            "metadata.category" -> "control",
            "metadata.proofOf" -> message.proofOf,
            "metadata.page" -> message.page
          )).map {
            case seq if seq.nonEmpty => seq.headOption
            case _ => None
          }
        )
      }

      /**
        * Handles the `ListProofs` message.
        *
        * @param message  The message to handle.
        * @return         A `Future` value containing all the proofs of the user,
        *                 or an empty `Seq` if no files could be found.
        * @note           Message `ListProof` does not alter the state of the user.
        */
      def apply(message: ListProofs): Future[Seq[MetaFile]] = getUser(message.userId).flatMap {
        case None => operationNotAllowed(message)
        case Some(user) => authorize(message, user.id.get, () =>
          fsService.find(Json.obj(
            "metadata.userId" -> user.id,
            "metadata.category" -> "control"
          ))
        )
      }
    }

    /**
      * Disables any conversion that is not allowed from state `New`.
      */
    class FromNew extends Transduction {

      def state = New

      override def apply(message: RequestVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ApproveVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefuseVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RevokeApproval): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: List): Future[Seq[User]] = operationNotAllowed(message)
      override def apply(message: AddProof): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetProofOf): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteProof): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: FindProof): Future[Option[MetaFile]] = operationNotAllowed(message)
      override def apply(message: ListProofs): Future[Seq[MetaFile]] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Registered`.
      */
    class FromRegistered extends Transduction {

      def state = Registered

      override def apply(message: Save): Future[(Ztate, User)] = operationNotAllowed(message)
      override def apply(message: AcquireForVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ApproveVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefuseVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RevokeApproval): Future[Ztate] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `AwaitingVerification`.
      */
    class FromAwaitingVerification extends Transduction {

      authorizeImpl("FindProof") = (userId: String, token: Token) => token.roles.contains(Auditor.id)
      authorizeImpl("ListProofs") = (userId: String, token: Token) => token.roles.contains(Auditor.id)

      def state = AwaitingVerification

      override def apply(message: Save): Future[(Ztate, User)] = operationNotAllowed(message)
      override def apply(message: RequestVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ApproveVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefuseVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RevokeApproval): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AddProof): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetProofOf): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteProof): Future[Ztate] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Verification`.
      */
    class FromVerification extends Transduction {

      authorizeImpl("FindProof") = (userId: String, token: Token) => token.roles.contains(Auditor.id)
      authorizeImpl("ListProofs") = (userId: String, token: Token) => token.roles.contains(Auditor.id)

      def state = Verification

      override def apply(message: Save): Future[(Ztate, User)] = operationNotAllowed(message)
      override def apply(message: RequestVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RevokeApproval): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AddProof): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetProofOf): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteProof): Future[Ztate] = operationNotAllowed(message)
    }

    /**
      * Disables any conversion that is not allowed from state `Approved`.
      */
    class FromApproved extends Transduction {

      authorizeImpl("FindProof") = (userId: String, token: Token) => token.roles.contains(Auditor.id)
      authorizeImpl("ListProofs") = (userId: String, token: Token) => token.roles.contains(Auditor.id)

      def state = Approved

      override def apply(message: Save): Future[(Ztate, User)] = operationNotAllowed(message)
      override def apply(message: RequestVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AcquireForVerification): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: ApproveVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: RefuseVerificationRequest): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: AddProof): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: SetProofOf): Future[Ztate] = operationNotAllowed(message)
      override def apply(message: DeleteProof): Future[Ztate] = operationNotAllowed(message)
    }
  }
}
