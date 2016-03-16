/*#
  * @file User.scala
  * @begin 31-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json._
import services.auth.users.fsm
import utils.common.Validator
import utils.common.Formats._
import models.common.{Address, JsEntity, HistoryEvent, State}
import models.common.Address._
import models.common.HistoryEvent._
import models.common.State._

import Password._
import MetaAccount._

/**
  * Represents a user.
  *
  * @constructor  Initializes a new instance of the [[User]] class.
  * @param json   The user data as JSON.
  */
class User protected(protected var json: JsValue) extends JsEntity with api.User {

  def email = json as (__ \ 'email).readNullable[String]
  def email_= (v: Option[String]) = setValue((__ \ 'email), Json.toJson(v))
  def username = json as (__ \ 'username).readNullable[String]
  def username_= (v: Option[String]) = setValue((__ \ 'username), Json.toJson(v))
  def password = json as (__ \ 'password).readNullable[Password]
  def password_= (v: Option[Password]) = setValue((__ \ 'password), Json.toJson(v))
  def mobile = json as (__ \ 'mobile).readNullable[String]
  def mobile_= (v: Option[String]) = setValue((__ \ 'mobile), Json.toJson(v))
  def firstName = json as (__ \ 'firstName).readNullable[String]
  def firstName_= (v: Option[String]) = setValue((__ \ 'firstName), Json.toJson(v))
  def lastName = json as (__ \ 'lastName).readNullable[String]
  def lastName_= (v: Option[String]) = setValue((__ \ 'lastName), Json.toJson(v))
  def birthDate = json as (__ \ 'birthDate).readNullable[LocalDate]
  def birthDate_= (v: Option[LocalDate]) = setValue((__ \ 'birthDate), Json.toJson(v))
  def lang = json as (__ \ 'lang).readNullable[String]
  def lang_= (v: Option[String]) = setValue((__ \ 'lang), Json.toJson(v))
  def biography = json as (__ \ 'biography).readNullable[String]
  def biography_= (v: Option[String]) = setValue((__ \ 'biography), Json.toJson(v))
  def company = json as (__ \ 'company).readNullable[String]
  def company_= (v: Option[String]) = setValue((__ \ 'company), Json.toJson(v))
  def website = json as (__ \ 'website).readNullable[String]
  def website_= (v: Option[String]) = setValue((__ \ 'website), Json.toJson(v))
  def state = json as (__ \ 'state).readNullable[State]
  def state_= (v: Option[State]) = setValue((__ \ 'state), Json.toJson(v))
  def newsletterOpt = json as (__ \ 'newsletter).readNullable[Boolean]
  def newsletterOpt_= (v: Option[Boolean]) = setValue((__ \ 'newsletter), Json.toJson(v))
  def newsletter = newsletterOpt.getOrElse(false)
  def newsletter_= (v: Boolean) = newsletterOpt_=(Some(v))
  def publicOpt = json as (__ \ 'public).readNullable[Boolean]
  def publicOpt_= (v: Option[Boolean]) = setValue((__ \ 'public), Json.toJson(v))
  def public = publicOpt.getOrElse(false)
  def public_= (v: Boolean) = publicOpt_=(Some(v))
  def addresses = json as (__ \ 'addresses).readNullable[List[Address]]
  def addresses_= (v: Option[List[Address]]) = setValue((__ \ 'addresses), Json.toJson(v))
  def metaAccounts = json as (__ \ 'metaAccounts).readNullable[List[MetaAccount]]
  def metaAccounts_= (v: Option[List[MetaAccount]]) = setValue((__ \ 'metaAccounts), Json.toJson(v))
  def history = json as (__ \ 'history).readNullable[List[HistoryEvent]]
  def history_= (v: Option[List[HistoryEvent]]) = setValue((__ \ 'history), Json.toJson(v))

  def copy(user: User): User = new User(this.json.as[JsObject] ++ user.json.as[JsObject])
  def copy(json: JsValue): User = User(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(user, _) => user
    case JsError(_) => new User(this.json)
  }
}

/**
  * Factory class for creating [[User]] instances.
  */
object User extends Validator {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  val MinUsernameLength = configuration.getInt("auth.minUsernameLength").getOrElse(3)

  /**
    * Initializes a new instance of the [[User]] class with the specified JSON.
    *
    * @param json     The user data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[User] = {
    { relaxed match {
      case Some(r) => validateUser(r)
      case _ => validateUser
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new User(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[User]] class with the specified values.
    *
    * @param id           The identifier of the user.
    * @param email        The email address of the user.
    * @param username     The username of the user.
    * @param password     The hashed password of the user.
    * @param mobile       The mobile number of the user.
    * @param firstName    The first name of the user.
    * @param lastName     The last name of the user.
    * @param birthDate    The birth date of the user.
    * @param lang         The language of the user.
    * @param biography    The biography of the user.
    * @param company      The name of the company the user works for.
    * @param website      The website of the user.
    * @param state        One of the [[services.auth.users.UserFsm]] values.
    * @param newsletter   A Boolean value indicating whether the user is subscribed to the newsletter.
    * @param public       A Boolean value indicating whether or not the user is public.
    * @param addresses    The addresses of the user.
    * @param metaAccounts The accounts the user owns or is granted access to.
    * @param history      The history of the user.
    * @param creationTime The time the user was created.
    * @return             A new instance of the [[User]] class.
    */
  def apply(
    id: Option[String] = None,
    email: Option[String] = None,
    username: Option[String] = None,
    password: Option[Password] = None,
    mobile: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    birthDate: Option[LocalDate] = None,
    lang: Option[String] = None,
    biography: Option[String] = None,
    company: Option[String] = None,
    website: Option[String] = None,
    state: Option[State] = None,
    newsletter: Option[Boolean] = None,
    public: Option[Boolean] = None,
    addresses: Option[List[Address]] = None,
    metaAccounts: Option[List[MetaAccount]] = None,
    history: Option[List[HistoryEvent]] = None,
    creationTime: Option[DateTime] = None
  ): User = new User(
    userWrites.writes(
      id,
      email,
      username,
      password,
      mobile,
      firstName,
      lastName,
      birthDate,
      lang,
      biography,
      company,
      website,
      state,
      newsletter,
      public,
      addresses,
      metaAccounts,
      history,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[User]].
    *
    * @param user The [[User]] to extract the content from.
    * @return     An `Option` that contains the extracted data,
    *             or `None` if `user` is `null`.
    */
  def unapply(user: User) = {
    if (user eq null) None
    else Some((
      user.id,
      user.email,
      user.username,
      user.password,
      user.mobile,
      user.firstName,
      user.lastName,
      user.birthDate,
      user.lang,
      user.biography,
      user.company,
      user.website,
      user.state,
      user.newsletter,
      user.public,
      user.addresses,
      user.metaAccounts,
      user.history,
      user.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[User]] to/from JSON.
    */
  implicit val userFormat: Format[User] = userFormat(None)
  def userFormat(relaxed: Option[Boolean]): Format[User] = new Format[User] {
    def reads(json: JsValue) = User(json, relaxed)
    def writes(user: User) = user.json
  }

  /**
    * Serializes a [[User]] to JSON.
    * @note Used internally by `apply`.
    */
  private val userWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'email).writeNullable[String] ~
    (__ \ 'username).writeNullable[String] ~
    (__ \ 'password).writeNullable[Password] ~
    (__ \ 'mobile).writeNullable[String] ~
    (__ \ 'firstName).writeNullable[String] ~
    (__ \ 'lastName).writeNullable[String] ~
    (__ \ 'birthDate).writeNullable[LocalDate] ~
    (__ \ 'lang).writeNullable[String] ~
    (__ \ 'biography).writeNullable[String] ~
    (__ \ 'company).writeNullable[String] ~
    (__ \ 'website).writeNullable[String] ~
    (__ \ 'state).writeNullable[State] ~
    (__ \ 'newsletter).writeNullable[Boolean] ~
    (__ \ 'public).writeNullable[Boolean] ~
    (__ \ 'addresses).writeNullable[List[Address]] ~
    (__ \ 'metaAccounts).writeNullable[List[MetaAccount]] ~
    (__ \ 'history).writeNullable[List[HistoryEvent]] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of the password.
    */
  private val password = (__ \ 'password).json.pick[JsValue] andThen validatePassword

  /**
    * Validates the JSON representation of the user state.
    */
  private val state = (__ \ 'state).json.pick[JsValue] andThen validateState

  /**
    * Validates the JSON representation of the address list.
    */
  private def validateAddresses(relaxed: Option[Boolean]) = {
    verifyingIf((arr: JsArray) => arr.value.nonEmpty)(Reads.list(validateAddress(relaxed.getOrElse(true))))
  }
  private def addresses: Reads[JsArray] = addresses(None)
  private def addresses(relaxed: Option[Boolean]): Reads[JsArray] = {
    (__ \ 'addresses).json.pick[JsArray] andThen validateAddresses(relaxed)
  }

  /**
    * Validates the JSON representation of the account list.
    */
  private def validateMetaAccounts(relaxed: Option[Boolean]) = {
    verifyingIf((arr: JsArray) => arr.value.nonEmpty) { relaxed match {
      case Some(r) => validateMetaAccount(r)
      case _ => validateMetaAccount
    }}
  }
  private def metaAccounts: Reads[JsArray] = metaAccounts(None)
  private def metaAccounts(relaxed: Option[Boolean]): Reads[JsArray] = {
    (__ \ 'metaAccounts).json.pick[JsArray] andThen validateMetaAccounts(relaxed)
  }

  /**
    * Validates the JSON representation of the user history.
    */
  private val validateHistory = verifyingIf((arr: JsArray) =>
    arr.value.nonEmpty)(Reads.list(validateHistoryEvent))
  private val history: Reads[JsArray] = {
    (__ \ 'history).json.pick[JsArray] andThen validateHistory
  }

  /**
    * Validates the JSON representation of a [[User]].
    *
    * @return A `Reads` that validates the JSON representation of a [[User]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateUser = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'email).json.pickBranch orEmpty) ~
    ((__ \ 'username).json.pickBranch orEmpty) ~
    ((__ \ 'password).json.copyFrom(password) orEmpty) ~
    ((__ \ 'mobile).json.pickBranch orEmpty) ~
    ((__ \ 'firstName).json.pickBranch orEmpty) ~
    ((__ \ 'lastName).json.pickBranch orEmpty) ~
    ((__ \ 'birthDate).json.pickBranch orEmpty) ~
    ((__ \ 'lang).json.pickBranch orEmpty) ~
    ((__ \ 'biography).json.pickBranch orEmpty) ~
    ((__ \ 'company).json.pickBranch orEmpty) ~
    ((__ \ 'website).json.pickBranch orEmpty) ~
    ((__ \ 'state).json.copyFrom(state) orEmpty) ~
    ((__ \ 'newsletter).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'public).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'addresses).json.copyFrom(addresses) orEmpty) ~
    ((__ \ 'metaAccounts).json.copyFrom(metaAccounts) orEmpty) ~
    ((__ \ 'history).json.copyFrom(history) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[User]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[User]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateUser(relaxed: Boolean) = (
    ((__ \ 'email).json.pickBranch(Reads.of[JsString] <~ Reads.email) orEmptyIf relaxed) ~
    ((__ \ 'username).json.pickBranch(Reads.of[JsString] <~ Reads.minLength[String](MinUsernameLength)) orEmptyIf relaxed) ~
    ((__ \ 'password).json.copyFrom(password) orEmptyIf relaxed) ~
    ((__ \ 'mobile).json.pickBranch(Reads.of[JsString] <~ phoneNumber) orEmpty) ~
    ((__ \ 'firstName).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'lastName).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'birthDate).json.pickBranch(Reads.of[JsString] <~ isoDate) orEmptyIf relaxed) ~
    ((__ \ 'lang).json.pickBranch(Reads.of[JsString] <~ isoLang) orEmpty) ~
    ((__ \ 'biography).json.pickBranch orEmpty) ~
    ((__ \ 'company).json.pickBranch orEmpty) ~
    ((__ \ 'website).json.pickBranch(Reads.of[JsString] <~ website) orEmpty) ~
    ((__ \ 'addresses).json.copyFrom(addresses(Some(relaxed))) orEmpty)
  ).reduce
}
