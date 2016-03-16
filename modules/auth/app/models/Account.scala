/*#
  * @file Account.scala
  * @begin 29-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsEntity

/**
  * Represents an API consumer or user account.
  *
  * @constructor  Initializes a new instance of the [[Account]] class.
  * @param json   The account data as JSON.
  */
class Account protected(protected var json: JsValue) extends JsEntity {

  def ownerId = json as (__ \ 'ownerId).readNullable[String]
  def ownerId_= (v: Option[String]) = setValue((__ \ 'ownerId), Json.toJson(v))
  def roles = json as (__ \ 'roles).readNullable[List[Int]]
  def roles_= (v: Option[List[Int]]) = setValue((__ \ 'roles), Json.toJson(v))
  def permissions = json as (__ \ 'permissions).readNullable[List[Int]]
  def permissions_= (v: Option[List[Int]]) = setValue((__ \ 'permissions), Json.toJson(v))

  def copy(account: Account): Account = new Account(this.json.as[JsObject] ++ account.json.as[JsObject])
  def copy(json: JsValue): Account = Account(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(account, _) => account
    case JsError(_) => new Account(this.json)
  }
}

/**
  * Factory class for creating [[Account]] instances.
  */
object Account extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Account]] class with the specified JSON.
    *
    * @param json The account data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Account] = {
    validateAccount.reads(json).fold(
      valid = { validated => JsSuccess(new Account(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Account]] class with the specified values.
    *
    * @param id           The identifier of the account.
    * @param ownerId      The identifier of the API consumer or user that owns the account.
    * @param roles        The roles associated with the account.
    * @param permissions  The permissions granted to the account.
    * @param creationTime The time the account was created.
    * @return             A new instance of the [[Account]] class.
    */
  def apply(
    id: Option[String] = None,
    ownerId: Option[String] = None,
    roles: Option[List[Int]] = None,
    permissions: Option[List[Int]] = None,
    creationTime: Option[DateTime] = None
  ): Account = new Account(
    accountWrites.writes(
      id,
      ownerId,
      roles,
      permissions,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Account]].
    *
    * @param account  The [[Account]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `account` is `null`.
    */
  def unapply(account: Account) = {
    if (account eq null) None
    else Some((
      account.id,
      account.ownerId,
      account.roles,
      account.permissions,
      account.creationTime
    ))
  }

  /**
    * Serializes/Deserializes an [[Account]] to/from JSON.
    */
  implicit val accountFormat = new Format[Account] {
    def reads(json: JsValue) = Account(json)
    def writes(account: Account) = account.json
  }

  /**
    * Serializes an [[Account]] to JSON.
    * @note Used internally by `apply`.
    */
  private val accountWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'ownerId).writeNullable[String] ~
    (__ \ 'roles).writeNullable[List[Int]] ~
    (__ \ 'permissions).writeNullable[List[Int]] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of a [[Account]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Account]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateAccount = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'ownerId).json.pickBranch orEmpty) ~
    ((__ \ 'roles).json.pickBranch(Reads.of[JsArray]) orEmpty) ~
    ((__ \ 'permissions).json.pickBranch(Reads.of[JsArray]) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce
}
