/*#
  * @file Credentials.scala
  * @begin 16-Apr-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

/**
  * Provides the piece of information that verifies the identity of a subject.
  *
  * @constructor      Initializes a new instance of the [[Credentials]] class
  *                   with the specified principal and secret.
  * @param principal  The subject represented by an account.
  * @param secret     The secret that grants access to the account.
  */
class Credentials private(
  val principal: String,
  val secret: String
) extends api.Credentials

/**
  * Factory class for creating [[Credentials]] instances.
  */
object Credentials {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import play.api.libs.json.Reads._

  /** Creates a new instance of the [[Credentials]] class with the specified
    * principal and secret.
    *
    * @param principal  The subject represented by an account.
    * @param secret     The secret that grants access to the account.
    * @return           A new instance of the [[Credentials]] class.
    */
  def apply(
    principal: String,
    secret: String
  ) = new Credentials(principal, secret)

  /**
    * Extracts the content of the specified [[Credentials]].
    *
    * @param credentials  The [[Credentials]] to extract the content from.
    * @return             An `Option` that contains the extracted data, or
    *                     `None` if `credentials` is `null`.
    */
  def unapply(credentials: Credentials) = {
    if (credentials eq null) None
    else Some((
      credentials.principal,
      credentials.secret
    ))
  }

  /**
    * Serializes/Deserializes a [[Credentials]] to/from JSON.
    */
  implicit val credentialsFormat = new Format[Credentials] {
    def reads(json: JsValue) = (
      (__ \ 'principal).read[String] ~
      (__ \ 'secret).read[String]
    )(Credentials.apply(_, _)).reads(json)

    def writes(credentials: Credentials) = Json.obj(
      "principal" -> credentials.principal,
      "secret" -> credentials.secret
    )
  }

  /**
    * Validates the JSON representation of a [[Credentials]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Credentials]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateCredentials = (
    ((__ \ 'principal).json.pickBranch) ~
    ((__ \ 'secret).json.pickBranch)
  ).reduce
}
