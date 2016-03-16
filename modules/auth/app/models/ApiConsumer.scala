/*#
  * @file ApiConsumer.scala
  * @begin 24-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsEntity

/**
  * Represents any client system that uses the application API.
  *
  * @constructor  Initializes a new instance of the [[ApiConsumer]] class.
  * @param json   The API consumer data as JSON.
  */
class ApiConsumer protected(protected var json: JsValue) extends JsEntity with api.ApiConsumer {

  def accountId = json as (__ \ 'accountId).readNullable[String]
  def accountId_= (v: Option[String]) = setValue((__ \ 'accountId), Json.toJson(v))
  def ownerId = json as (__ \ 'ownerId).readNullable[String]
  def ownerId_= (v: Option[String]) = setValue((__ \ 'ownerId), Json.toJson(v))
  def name = json as (__ \ 'name).readNullable[String]
  def name_= (v: Option[String]) = setValue((__ \ 'name), Json.toJson(v))
  def description = json as (__ \ 'description).readNullable[String]
  def description_= (v: Option[String]) = setValue((__ \ 'description), Json.toJson(v))
  def website = json as (__ \ 'website).readNullable[String]
  def website_= (v: Option[String]) = setValue((__ \ 'website), Json.toJson(v))
  def apiKey = json as (__ \ 'apiKey).readNullable[String]
  def apiKey_= (v: Option[String]) = setValue((__ \ 'apiKey), Json.toJson(v))
  def nativeOpt = json as (__ \ 'native).readNullable[Boolean]
  def nativeOpt_= (v: Option[Boolean]) = setValue((__ \ 'native), Json.toJson(v))
  def native = nativeOpt.getOrElse(false)
  def native_= (v: Boolean) = nativeOpt_=(Some(v))

  def copy(apiConsumer: ApiConsumer): ApiConsumer = new ApiConsumer(this.json.as[JsObject] ++ apiConsumer.json.as[JsObject])
  def copy(json: JsValue): ApiConsumer = ApiConsumer(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(apiConsumer, _) => apiConsumer
    case JsError(_) => new ApiConsumer(this.json)
  }
}

/**
  * Factory class for creating [[ApiConsumer]] instances.
  */
object ApiConsumer extends Validator {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  private val MinDescriptionLength = configuration.getInt("auth.apiConsumer.minDescriptionLength").getOrElse(10)
  private val MaxDescriptionLength = configuration.getInt("auth.apiConsumer.maxDescriptionLength").getOrElse(200)

  /**
    * Initializes a new instance of the [[ApiConsumer]] class with the specified JSON.
    *
    * @param json     The API consumer data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[ApiConsumer] = {
    { relaxed match {
      case Some(r) => validateApiConsumer(r)
      case _ => validateApiConsumer
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new ApiConsumer(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[ApiConsumer]] class with the specified values.
    *
    * @param id           The identifier of the API consumer
    * @param accountId    The identifier of the account associated with the API consumer.
    * @param ownerId      The identifier of the account that owns the API consumer.
    * @param name         The name of the API consumer.
    * @param description  The description of the API consumer.
    * @param website      The website of the API consumer.
    * @param apiKey       The secret API key.
    * @param native       A Boolean value indicating whether or not the API consumer
    *                     is native, i.e. it does not come from third parties.
    * @param creationTime The time the API consumer was created.
    * @return             A new instance of the [[ApiConsumer]] class.
    */
  def apply(
    id: Option[String] = None,
    accountId: Option[String] = None,
    ownerId: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    website: Option[String] = None,
    apiKey: Option[String] = None,
    native: Option[Boolean] = None,
    creationTime: Option[DateTime] = None
  ): ApiConsumer = new ApiConsumer(
    apiConsumerWrites.writes(
      id,
      accountId,
      ownerId,
      name,
      description,
      website,
      apiKey,
      native,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[ApiConsumer]].
    *
    * @param apiConsumer  The [[ApiConsumer]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `apiConsumer` is `null`.
    */
  def unapply(apiConsumer: ApiConsumer) = {
    if (apiConsumer eq null) None
    else Some((
      apiConsumer.id,
      apiConsumer.accountId,
      apiConsumer.ownerId,
      apiConsumer.name,
      apiConsumer.description,
      apiConsumer.website,
      apiConsumer.apiKey,
      apiConsumer.native,
      apiConsumer.creationTime
    ))
  }

  /**
    * Serializes/Deserializes an [[ApiConsumer]] to/from JSON.
    */
  implicit val apiConsumerFormat: Format[ApiConsumer] = apiConsumerFormat(None)
  def apiConsumerFormat(relaxed: Option[Boolean]) = new Format[ApiConsumer] {
    def reads(json: JsValue) = ApiConsumer(json, relaxed)
    def writes(apiConsumer: ApiConsumer) = apiConsumer.json
  }

  /**
    * Serializes an [[ApiConsumer]] to JSON.
    * @note Used internally by `apply`.
    */
  private val apiConsumerWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'accountId).writeNullable[String] ~
    (__ \ 'ownerId).writeNullable[String] ~
    (__ \ 'name).writeNullable[String] ~
    (__ \ 'description).writeNullable[String] ~
    (__ \ 'website).writeNullable[String] ~
    (__ \ 'apiKey).writeNullable[String] ~
    (__ \ 'native).writeNullable[Boolean] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of an [[ApiConsumer]].
    *
    * @return A `Reads` that validates the JSON representation of an [[ApiConsumer]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateApiConsumer = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'accountId).json.pickBranch orEmpty) ~
    ((__ \ 'ownerId).json.pickBranch orEmpty) ~
    ((__ \ 'name).json.pickBranch orEmpty) ~
    ((__ \ 'description).json.pickBranch orEmpty) ~
    ((__ \ 'website).json.pickBranch orEmpty) ~
    ((__ \ 'apiKey).json.pickBranch orEmpty) ~
    ((__ \ 'native).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of an [[ApiConsumer]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of an [[ApiConsumer]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateApiConsumer(relaxed: Boolean) = (
    ((__ \ 'name).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'description).json.pickBranch(Reads.of[JsString] <~ Reads.minLength[String](
      MinDescriptionLength
    ) andKeep Reads.of[JsString] <~ Reads.maxLength[String](
      MaxDescriptionLength
    )) orEmptyIf relaxed) ~
    ((__ \ 'website).json.pickBranch(Reads.of[JsString] <~ website) orEmpty) ~
    ((__ \ 'apiKey).json.pickBranch orEmpty)
  ).reduce
}
