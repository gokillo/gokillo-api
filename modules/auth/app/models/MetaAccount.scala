/*#
  * @file MetaAccount.scala
  * @begin 31-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsModel

/**
  * Represents an account a user owns or is granted access to.
  *
  * When an account is shared among many users, each user has to activate
  * it and specify whether or not it is the default account.  
  *
  * @constructor  Initializes a new instance of the [[MetaAccount]] class.
  * @param json   The meta-account data as JSON.
  */
class MetaAccount protected(protected var json: JsValue) extends JsModel with api.Account {

  def id = json as (__ \ 'id).readNullable[String]
  def id_= (v: Option[String]) = setValue((__ \ 'id), Json.toJson(v))
  def name = json as (__ \ 'name).readNullable[String]
  def name_= (v: Option[String]) = setValue((__ \ 'name), Json.toJson(v))
  def activationTime = json as (__ \ 'activationTime).readNullable[DateTime]
  def activationTime_= (v: Option[DateTime]) = setValue((__ \ 'activationTime), Json.toJson(v))
  def defaultOpt = json as (__ \ 'default).readNullable[Boolean]
  def defaultOpt_= (v: Option[Boolean]) = setValue((__ \ 'default), Json.toJson(v))
  def default = defaultOpt.getOrElse(false)
  def default_= (v: Boolean) = defaultOpt_=(Some(v))

  def copy(metaAccount: MetaAccount): MetaAccount = new MetaAccount(this.json.as[JsObject] ++ metaAccount.json.as[JsObject])
  def copy(json: JsValue): MetaAccount = MetaAccount(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(metaAccount, _) => metaAccount
    case JsError(_) => new MetaAccount(this.json)
  }

  /**
    * Returns a Boolean value indicating whether or not the account is active.
    * @return `true` if the account is active; otherwise, `false`.
    */
  def active = activationTime.map(_.isBefore(DateTime.now(DateTimeZone.UTC))).getOrElse(false)
}

/**
  * Factory class for creating [[MetaAccount]] instances.
  */
object MetaAccount extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[MetaAccount]] class with the specified JSON.
    *
    * @param json     The meta-account data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[MetaAccount] = {
    { relaxed match {
      case Some(r) => validateMetaAccount(r)
      case _ => validateMetaAccount
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new MetaAccount(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[MetaAccount]] class with the specified values.
    *
    * @param id         The identifier of the account.
    * @param name       The name associated with the account.
    * @param activationTime An `Option` that contains the time the account was
    *                   activated, or `None` if the account has not been activated yet.
    * @param default    A Boolean value indicating whether or not the account
    *                   identified by `id` is the default account for a given user.
    * @return           A new instance of the [[MetaAccount]] class.
    */
  def apply(
    id: Option[String] = None,
    name: Option[String] = None,
    activationTime: Option[DateTime] = None,
    default: Option[Boolean] = None
  ): MetaAccount = new MetaAccount(
    metaAccountWrites.writes(
      id,
      name,
      activationTime,
      default
    )
  )

  /**
    * Extracts the content of the specified [[MetaAccount]].
    *
    * @param metaAccount  The [[MetaAccount]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `metaAccount` is `null`.
    */
  def unapply(metaAccount: MetaAccount) = {
    if (metaAccount eq null) None
    else Some((
      metaAccount.id,
      metaAccount.name,
      metaAccount.activationTime,
      metaAccount.default
    ))
  }

  /**
    * Serializes/Deserializes a [[MetaAccount]] to/from JSON.
    */
  implicit def metaAccountFormat: Format[MetaAccount] = metaAccountFormat(None)
  def metaAccountFormat(relaxed: Option[Boolean]) = new Format[MetaAccount] {
    def reads(json: JsValue) = MetaAccount(json, relaxed)
    def writes(metaAccount: MetaAccount) = metaAccount.json
  }

  /**
    * Serializes a [[MetaAccount]] to JSON.
    * @note Used internally by `apply`.
    */
  private val metaAccountWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'name).writeNullable[String] ~
    (__ \ 'activationTime).writeNullable[DateTime] ~
    (__ \ 'default).writeNullable[Boolean]
  ).tupled
  
  /**
    * Validates the JSON representation of a [[MetaAccount]].
    *
    * @return A `Reads` that validates the JSON representation of a [[MetaAccount]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateMetaAccount = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'name).json.pickBranch orEmpty) ~
    ((__ \ 'activationTime).json.pickBranch(Reads.of[JsString] <~ isoDateTime) orEmpty) ~
    ((__ \ 'default).json.pickBranch(Reads.of[JsBoolean]) orEmpty) 
  ).reduce

  /**
    * Validates the JSON representation of a [[MetaAccount]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[MetaAccount]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateMetaAccount(relaxed: Boolean) = (
    ((__ \ 'name).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'default).json.pickBranch(Reads.of[JsBoolean]) orEmptyIf relaxed) 
  ).reduce
}
