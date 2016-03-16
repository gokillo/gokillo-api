/*#
  * @file Address.scala
  * @begin 31-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._
import utils.common.Validator

/**
  * Represents the address of a physical location.
  *
  * @constructor  Initializes a new instance of the [[Address]] class.
  * @param json   The address data as JSON.
  */
class Address protected(protected var json: JsValue) extends JsModel with api.Address {

  def name = json as (__ \ 'name).readNullable[String]
  def name_= (v: Option[String]) = setValue((__ \ 'name), Json.toJson(v))
  def careOf = json as (__ \ 'careOf).readNullable[String]
  def careOf_= (v: Option[String]) = setValue((__ \ 'careOf), Json.toJson(v))
  def street = json as (__ \ 'street).readNullable[String]
  def street_= (v: Option[String]) = setValue((__ \ 'street), Json.toJson(v))
  def houseNr = json as (__ \ 'houseNr).readNullable[String]
  def houseNr_= (v: Option[String]) = setValue((__ \ 'houseNr), Json.toJson(v))
  def zip = json as (__ \ 'zip).readNullable[String]
  def zip_= (v: Option[String]) = setValue((__ \ 'zip), Json.toJson(v))
  def city = json as (__ \ 'city).readNullable[String]
  def city_= (v: Option[String]) = setValue((__ \ 'city), Json.toJson(v))
  def state = json as (__ \ 'state).readNullable[String]
  def state_= (v: Option[String]) = setValue((__ \ 'state), Json.toJson(v))
  def country = json as (__ \ 'country).readNullable[String]
  def country_= (v: Option[String]) = setValue((__ \ 'country), Json.toJson(v))
  def timeZone = json as (__ \ 'timeZone).readNullable[String]
  def timeZone_= (v: Option[String]) = setValue((__ \ 'timeZone), Json.toJson(v))
  def phone = json as (__ \ 'phone).readNullable[String]
  def phone_= (v: Option[String]) = setValue((__ \ 'phone), Json.toJson(v))
  def defaultOpt = json as (__ \ 'default).readNullable[Boolean]
  def defaultOpt_= (v: Option[Boolean]) = setValue((__ \ 'default), Json.toJson(v))
  def default = defaultOpt.getOrElse(false)
  def default_= (v: Boolean) = defaultOpt_=(Some(v))

  def copy(address: Address): Address = new Address(this.json.as[JsObject] ++ address.json.as[JsObject])
  def copy(json: JsValue): Address = Address(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(address, _) => address
    case JsError(_) => new Address(this.json)
  }
}

/**
  * Factory class for creating [[Address]] instances.
  */
object Address extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Address]] class with the specified JSON.
    *
    * @param json     The address data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Address] = {
    validateAddress(relaxed.getOrElse(true)).reads(json).fold(
      valid = { validated => JsSuccess(new Address(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Address]] class with the specified values.
    *
    * @param name     Any arbitrary, user defined, address name.
    * @param careOf   Who should receive correspondence in case the addressee
    *                 is not at the usual place.
    * @param street   The street address.
    * @param houseNr  The house number.
    * @param zip      The zip code.
    * @param city     The city address.
    * @param state    The state address.
    * @param country  The country address.
    * @param timeZone The time zone offset in +/-HH:MM format.
    * @param phone    The primary phone number.
    * @param default  A Boolean value indicating whether the address is the default one.
    * @return         A new instance of the [[Address]] class.
    */
  def apply(
    name: Option[String] = None,
    careOf: Option[String] = None,
    street: Option[String] = None,
    houseNr: Option[String] = None,
    zip: Option[String] = None,
    city: Option[String] = None,
    state: Option[String] = None,
    country: Option[String] = None,
    timeZone: Option[String] = None,
    phone: Option[String] = None,
    default: Option[Boolean] = None
  ): Address = new Address(
    addressWrites.writes(
      name,
      careOf,
      street,
      houseNr,
      zip,
      city,
      state,
      country,
      timeZone,
      phone,
      default
    )
  ) 

  /**
    * Extracts the content of the specified [[Address]].
    *
    * @param address  The [[Address]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `address` is `null`.
    */
  def unapply(address: Address) = {
    if (address eq null) None
    else Some((
      address.name,
      address.careOf,
      address.street,
      address.houseNr,
      address.zip,
      address.city,
      address.state,
      address.country,
      address.timeZone,
      address.phone,
      address.default
    ))
  }

  /**
    * Serializes/Deserializes an [[Address]] to/from JSON.
    */
  implicit val addressFormat: Format[Address] = addressFormat(None)
  def addressFormat(relaxed: Option[Boolean]) = new Format[Address] {
    def reads(json: JsValue) = Address(json, relaxed)
    def writes(address: Address) = address.json
  }

  /**
    * Serializes an [[Address]] to JSON.
    * @note Used internally by `apply`.
    */
  private val addressWrites = (
    (__ \ 'name).writeNullable[String] ~
    (__ \ 'careOf).writeNullable[String] ~
    (__ \ 'street).writeNullable[String] ~
    (__ \ 'houseNr).writeNullable[String] ~
    (__ \ 'zip).writeNullable[String] ~
    (__ \ 'city).writeNullable[String] ~
    (__ \ 'state).writeNullable[String] ~
    (__ \ 'country).writeNullable[String] ~
    (__ \ 'timeZone).writeNullable[String] ~
    (__ \ 'phone).writeNullable[String] ~
    (__ \ 'default).writeNullable[Boolean]
  ).tupled
 
  /**
    * Validates the JSON representation of an [[Address]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of an [[Address]].
    * @note           This validator is intended for JSON coming from inside the application.
    */
  def validateAddress = (
    ((__ \ 'name).json.pickBranch orEmpty) ~
    ((__ \ 'careOf).json.pickBranch orEmpty) ~
    ((__ \ 'street).json.pickBranch orEmpty) ~
    ((__ \ 'houseNr).json.pickBranch orEmpty) ~
    ((__ \ 'zip).json.pickBranch orEmpty) ~
    ((__ \ 'city).json.pickBranch orEmpty) ~
    ((__ \ 'state).json.pickBranch orEmpty) ~
    ((__ \ 'country).json.pickBranch orEmpty) ~
    ((__ \ 'timeZone).json.pickBranch orEmpty) ~
    ((__ \ 'default).json.pickBranch(Reads.of[JsBoolean]) orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of an [[Address]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of an [[Address]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateAddress(relaxed: Boolean) = (
    ((__ \ 'name).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'careOf).json.pickBranch orEmpty) ~
    ((__ \ 'street).json.pickBranch orEmpty) ~
    ((__ \ 'houseNr).json.pickBranch orEmpty) ~
    ((__ \ 'zip).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'city).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'state).json.pickBranch orEmpty) ~
    ((__ \ 'country).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'timeZone).json.pickBranch(Reads.of[JsString] <~ timeZone) orEmptyIf relaxed) ~
    ((__ \ 'default).json.pickBranch(Reads.of[JsBoolean]) orEmpty)
  ).reduce
}
