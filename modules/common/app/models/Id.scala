/*#
  * @file Id.scala
  * @begin 15-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._

/**
  * Represents an id of any type, like an unique identifier, an email,
  * or whatever else.
  *
  * @constructor  Initializes a new instance of the [[Id]] class.
  * @param json   The id as JSON.
  */
class Id protected(protected var json: JsValue) extends JsModel {

  val key = json.as[JsObject].keys.head

  def value = json as (__ \ key).readNullable[String]
  def value_= (v: Option[String]) = setValue((__ \ key), Json.toJson(v))

  def copy(json: JsValue) = throw new UnsupportedOperationException
  def copy(key: String = key, value: Option[String] = value) = new Id(Json.obj(key -> value))
}

/**
  * Factory class for creating [[Id]] instances.
  */
object Id {

  import play.api.libs.functional.syntax._
  import utils.common.typeExtensions._

  private final val DefaultKey = "id"

  /**
    * Initializes a new instance of the [[Id]] class.
    *
    * @param json The id as JSON.
    * @return     A `JsResult` value that contains the new class
    *             instance, or `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Id] = {
    validateId(json.as[JsObject].keys.head).reads(json).fold(
      valid = { validated => JsSuccess(new Id(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Id]] class.
    *
    * @param value  The id value.
    * @return       A new instance of the [[Id]] class.
    */
  def apply(value: Option[String]): Id = apply(DefaultKey, value)

  /**
    * Initializes a new instance of the [[Id]] class.
    *
    * @param key    The id name.
    * @param value  The id value.
    * @return       A new instance of the [[Id]] class.
    */
  def apply(key: String, value: Option[String]): Id = new Id(idWrites(key).writes(value))
  
  /**
    * Extracts the value of the specified [[Id]].
    *
    * @param id The [[Id]] to extract the value from.
    * @return   An `Option` that contains the extracted value,
    *           or `None` if `id` is `null`.
    */
  def unapply(id: Id) = {
    if (id eq null) None
    else Some((
      id.key,
      id.value
    ))
  }

  /**
    * Implicitily converts the specified string into an [[Id]].
    *
    * @param id The string to convert.
    * @return   The [[Id]] converted from `id`.
    */
  implicit def fromString(id: String) = new Id(Json.obj(DefaultKey -> id))

  /**
    * Implicitily converts the specified `Option` into an [[Id]].
    *
    * @param id `Option` to convert.
    * @return   The [[Id]] converted from `id`.
    */
  implicit def fromOption(id: Option[String]) = new Id(Json.obj(DefaultKey -> id))

  /**
    * Serializes/Deserializes an [[Id]] to/from JSON.
    */
  implicit val idFormat = new Format[Id] {
    def reads(json: JsValue) = Id(json)
    def writes(id: Id) = id.json
  }

  /**
    * Serializes an [[Id]] to JSON.
    * @note Used internally by `apply`.
    */
  private def idWrites(key: String) = ((__ \ key).writeNullable[String])

  /**
    * Validates the JSON representation of an [[Id]].
    *
    * @param key  The id name.
    * @return     A `Reads` that validates the JSON representation of an [[Id]].
    * @note       This validator is intended for JSON coming from both inside and
    *             outside the application.
    */
  def validateId(key: String) = ((__ \ key).json.pickBranch orEmpty)
}
