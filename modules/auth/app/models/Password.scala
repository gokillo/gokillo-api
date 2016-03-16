/*#
  * @file Password.scala
  * @begin 14-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

/**
  * Represents a password.
  *
  * @constructor  Initializes a new instance of the [[Password]] class with
  *               the specified value and salt.
  * @param value  The password value in either plaintext or hashtext.
  * @param salt   An `Option` value containing either the salt to be used to
  *               hash `value`, or `None` if the salt should be generated
  *               automatically.
  */
class Password private(value: String, salt: Option[String])
  extends brix.security.Password(value, salt) with api.Password {

  override def hash = {
    val hashed = super.hash
    new Password(hashed.value, hashed.salt)
  }
}

/**
  * Factory class for creating [[Password]] instances.
  */
object Password {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.data.validation.ValidationError
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /** Creates a new instance of the [[Password]] class with the specified
    * value and salt.
    *
    * @param value  The password value in either plaintext or hashtext.
    * @param salt   An `Option` value containing either the salt to be used
    *               to hash `value`, or `None` if the salt should be generated
    *               automatically.
    * @return       A new instance of the [[Password]] class.
    */
  def apply(value: String, salt: Option[String] = None) = new Password(value, salt)

  /**
    * Extracts the content of the specified [[Password]].
    *
    * @param password The [[Password]] to extract the content from.
    * @return         An `Option` that contains the extracted data, or `None`
    *                 if `password` is `null`.
    */
  def unapply(password: Password) = {
    if (password eq null) None
    else Some((
      password.value,
      password.salt
    ))
  }

  /**
    * Serializes/Deserializes a [[Password]] to/from JSON.
    */
  implicit val passwordFormat = new Format[Password] {
    def reads(json: JsValue) = (
      (__ \ 'value).read[String] ~
      (__ \ 'salt).readNullable[String]
    )(Password.apply(_, _)).reads(json)

    def writes(password: Password) = Json.obj(
      "value" -> password.value,
      "salt" -> password.salt
    )
  }

  /**
    * Validates the JSON representation of a [[Password]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Password]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validatePassword = (
    ((__ \ 'value).json.pickBranch(Reads.of[JsString] <~ password)) ~
    ((__ \ 'salt).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the password according to configured pattern.
    */
  private def password(implicit reads: Reads[String]) = Reads[String](js =>
    reads.reads(js).flatMap { value =>
      val password = Password(value)
      var result: JsResult[String] = JsSuccess(password.value)
      if (!password.isHashed) {
        val passwordPattern = configuration.getString("auth.passwordPattern").getOrElse(".*")
        if (!passwordPattern.r.unapplySeq(password.value).isDefined) {
          result = JsError(ValidationError("error.passwordPattern", passwordPattern))
        }
      }
      result
    }
  ) 
}
