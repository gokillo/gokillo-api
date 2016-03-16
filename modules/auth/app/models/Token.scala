/*#
  * @file Token.scala
  * @begin 24-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import scala.util.{Success, Failure}
import org.joda.time.{DateTime, DateTimeZone}
import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt._
import brix.crypto.{AES, Secret}
import play.api.Logger
import play.api.Play.current
import play.api.Play.configuration
import play.api.libs.json._
import play.api.libs.json.extensions._
import play.api.mvc.RequestHeader
import services.auth.AuthPlugin
import utils.common.env.localhost
import TokenType._

/**
  * Represents an authentication token.
  *
  * @constructor  Initializes a new instance of the [[Token]] class.
  * @param jwt    An instance of the `SignedJWT` class.
  */
class Token private(private val jwt: SignedJWT) extends api.Token with api.Jwt {

  import Token._

  private var _expirationTime = jwt.getJWTClaimsSet.getExpirationTime match {
    case dateTime if dateTime ne null => new DateTime(dateTime, DateTimeZone.UTC)
    case _ => new DateTime(
      jwt.getJWTClaimsSet.getIssueTime,
      DateTimeZone.UTC
    ).plusMinutes(TokenDuration)
  }

  private val _apiKey = jwt.getJWTClaimsSet.getCustomClaim("key") match {
    case key if key ne null => AES.decrypt(key.asInstanceOf[String], AppSecret) match {
      case Success(decrypted) => decrypted
      case Failure(e) => Logger.error("error decrypting api key from json web token", e); throw e
    }
    case _ => ""
  }

  private val _account = box(
    Json.parse(jwt.getJWTClaimsSet.getCustomClaim("acc").asInstanceOf[String])
  )

  private val _username = jwt.getJWTClaimsSet.getCustomClaim("usr") match {
    case usr if usr ne null => usr.asInstanceOf[String]
    case _ => "anonym"
  }

  def contentType = TokenType.withName(jwt.getHeader.getContentType)
  def tokenType = TokenType.withName(jwt.getHeader.getType.getType.split('/')(1))
  def id = jwt.getJWTClaimsSet.getJWTID
  def issuer = jwt.getJWTClaimsSet.getIssuer
  def subject = jwt.getJWTClaimsSet.getSubject
  def issueTime = new DateTime(jwt.getJWTClaimsSet.getIssueTime, DateTimeZone.UTC)
  def expirationTime = _expirationTime
  def apiKey = _apiKey
  def account = _account.id.getOrElse("")
  def accountOwner = _account.ownerId.getOrElse("")
  def roles = _account.roles.getOrElse(List[Int]())
  def permissions = _account.permissions.getOrElse(List[Int]())
  def username = _username

  /**
    * Returns the JSON Web Token representation of this [[Token]].
    * @return The JSON Web Token representation of this [[Token]].
    */
  def asJwt = jwt.serialize

  /**
    * Returns the plain JSON representation of this [[Token]].
    * @return The plain JSON representation of this [[Token]].
    */
  def asJson = {
    val claims = Json.parse(jwt.getPayload.toBytes)
    JsExtensions.buildJsObject(
      __ \ 'header -> Json.parse(jwt.getHeader.toString),
      __ \ 'claims -> claims.delete(__ \ 'key),
      __ \ 'claims \ 'acc -> Json.parse(claims.get(__ \ 'acc).as[JsString].value),
      __ \ 'claims \ 'acc \ 'roles -> Json.toJson(roles.map(Role.name(_))),
      __ \ 'claims \ 'exp -> JsNumber(expirationTime.getMillis / 1000)
    )
  }

  /**
    * Extends the duration of this [[Token]] according to the current configuration.
    */
  def extendDuration: Unit = {
    _expirationTime = DateTime.now(DateTimeZone.UTC).plusMinutes(TokenDuration)
  }

  /**
    * Makes this [[Token]] expire.
    */
  def expire: Unit = _expirationTime = issueTime

  /**
    * Returns a Boolean value indicating whether or not this [[Token]]
    * is expired.
    *
    * @return `true` if this [[Token]] is expired; otherwise, `false`.
    */
  def isExpired = _expirationTime.compareTo(DateTime.now(DateTimeZone.UTC)) < 0

  /**
    * Returns a Boolean value indicating whether or not this [[Token]]
    * is valid.
    *
    * @return `true` if this [[Token]] is valid; otherwise, `false`.
    */
  def isValid = jwt.verify(new MACVerifier(AppSecret.getBytes))
}

/**
  * Factory class for creating [[Token]] instances.
  */
object Token {

  import play.api.Logger
  import utils.common.TypeFactory

  private final val MinTokenDuration = 5
  private final val DefaultTokenDuration = 60
  private val typeFactory = new TypeFactory[Account] {}

  val BootstrapKey = Secret().value
  val AppSecret = configuration.getString("application.secret").getOrElse("")
  val TokenDuration = configuration.getInt("auth.tokenDuration").map {
    case duration if duration < MinTokenDuration =>
      Logger.info(s"token duration of $duration minutes is too short: forced to $MinTokenDuration minutes")
      MinTokenDuration
    case duration => duration
  } getOrElse DefaultTokenDuration

  /**
    * Returns a new instance of [[Account]] created from the specified JSON.
    *
    * @param json The JSON from which to create a new instance of [[Account]].
    * @return     A new instance of [[Account]] created from `json`.
    */
  private def box(json: JsValue): Account = typeFactory.newInstance(json)()

  /**
    * Initializes a new instance of the [[Token]] class with the specified values.
    *
    * @param tokenType  One of the [[TokenType]] values.
    * @param apiKey     The secret API key used to sign HTTP requests.
    * @param account    The account to create the authentication token for.
    * @param username   The username of the subject.
    * @param subject    An `Option` value containing the id of the subject
    *                   (i.e. user) to be associated with `account`; if `None`,
    *                   then the account owner is assumed.
    * @param extendable A Boolean value indicating whether or not the token
    *                   duration is extendable.
    * @param request    The current HTTP request header, if any.
    * @return           A new instance of the [[Token]] class.
    */
  def apply(
    tokenType: TokenType,
    apiKey: Option[String],
    account: Option[Account],
    username: Option[String],
    subject: Option[String],
    extendable: Boolean 
  )(implicit request: RequestHeader = null): Token = {
    /*
     * define standard claims
     */
    val claims = new JWTClaimsSet
    claims.setJWTID(AuthPlugin.generateTokenId)
    claims.setIssuer(localhost.endPoint.hostName)
    claims.setSubject(subject.getOrElse(account.map(_.ownerId.getOrElse("")).getOrElse("")))
    val now = DateTime.now(DateTimeZone.UTC)
    claims.setIssueTime(now.toDate)
    if (!extendable) claims.setExpirationTime(now.plusMinutes(TokenDuration).toDate)

    /*
     * define custom claims
     */
    AES.encrypt(apiKey.getOrElse(BootstrapKey), AppSecret) match {
      case Success(encrypted) => claims.setCustomClaim("key", encrypted)
      case Failure(e) => Logger.error("error encrypting api key into json web token", e); throw e
    }

    val acc = account.map { acc =>
      acc.asJson.set((__ \ 'creationTime) -> JsNumber(acc.creationTime.get.getMillis / 1000)).toString
    } getOrElse "{}"

    claims.setCustomClaim("acc", acc)
    username.map(claims.setCustomClaim("usr", _))

    /*
     * define jwt header
     */
    val header = new JWSHeader.Builder(JWSAlgorithm.HS256)
      .contentType("JWT")
      .`type`(new JOSEObjectType(s"JWT/$tokenType"))
      .build()

    /*
     * create jwt and sign it with the application's secret
     */
    val jwt = new SignedJWT(header, claims)
    jwt.sign(new MACSigner(AppSecret.getBytes))

    new Token(jwt)
  }

  /**
    * Initializes a new instance of the [[Token]] class with the specified
    * JSON Web Token.
    *
    * @param jwt  The JSON Web Token to create a new instance of the [[Token]]
    *             class from.
    * @return     A new instance of the [[Token]] class.
    */
  def apply(jwt: String): Token = {
    new Token(SignedJWT.parse(jwt))
  }
}
