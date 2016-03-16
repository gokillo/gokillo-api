/*#
  * @file RefId.scala
  * @begin 1-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._

/**
  * Represents a reference to an object id.
  *
  * @constructor  Initializes a new instance of the [[RefId]] class.
  * @param json   The reference id data as JSON.
  * @note         `RefId` provides agnostic APIs with the means to reference
  *               arbitray objects in other domains.
  */
class RefId protected(protected var json: JsValue) extends JsModel with api.RefId {

  def domain = json as (__ \ 'domain).read[String]
  def domain_= (v: String) = setValue((__ \ 'domain), Json.toJson(v))
  def name = json as (__ \ 'name).read[String]
  def name_= (v: String) = setValue((__ \ 'name), Json.toJson(v))
  def value = json \ "value" match {
    case v: JsString => v.value
    case v => v.as[Seq[String]].head
  }
  def value_= (v: String) = setValue((__ \ 'value), Json.toJson(v))
  def mvalue = json \ "value" match {
    case v: JsString => Seq(v.value)
    case v => v.as[Seq[String]]
  }
  def mvalue_= (v: Seq[String]) = setValue((__ \ 'value), Json.toJson(v))

  def copy(refId: RefId): RefId = new RefId(this.json.as[JsObject] ++ refId.json.as[JsObject])
  def copy(json: JsValue): RefId = RefId(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(refId, _) => refId
    case JsError(_) => new RefId(this.json)
  }
}

/**
  * Factory class for creating [[RefId]] instances.
  */
object RefId {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[RefId]] class with the specified JSON.
    *
    * @param json The reference id data as JSON.
    * @return     A `JsResult` value that contains the new class
    *             instance, or `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[RefId] = {
    validateRefId.reads(json).fold(
      valid = { validated => JsSuccess(new RefId(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[RefId]] class with the specified values.
    *
    * @param domain The domain of the object identified by the referenced id.
    * @param name   The name of the referenced id.
    * @param value  The value of the referenced id.
    * @return       A new instance of the [[RefId]] class.
    */
  def apply(
    domain: String,
    name: String,
    value: String
  ): RefId = new RefId(
    refIdWrites.writes(
      domain,
      name,
      Some(value),
      None
    )
  )

  /**
    * Initializes a new instance of the [[RefId]] class with the specified values.
    *
    * @param domain The domain of the object identified by the referenced id.
    * @param name   The name of the referenced id.
    * @param mvalue The values of the referenced id.
    * @return       A new instance of the [[RefId]] class.
    */
  def apply(
    domain: String,
    name: String,
    mvalue: Seq[String]
  ): RefId = new RefId(
    refIdWrites.writes(
      domain,
      name,
      None,
      Some(mvalue)
    )
  )

  /**
    * Extracts the content of the specified [[RefId]].
    *
    * @param refId  The [[RefId]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `refId` is `null`.
    */
  def unapply(refId: RefId) = {
    if (refId eq null) None
    else {
      val mvalue = refId.mvalue  
      Some((
        refId.domain,
        refId.name,
        if (mvalue.length > 1) mvalue else mvalue.head
      ))
    }
  }

  /**
    * Serializes/Deserializes a [[RefId]] to/from JSON.
    */
  implicit val refIdFormat = new Format[RefId] {
    def reads(json: JsValue) = RefId(json)
    def writes(refId: RefId) = refId.json
  }

  /**
    * Serializes a [[RefId]] to JSON.
    * @note Used internally by `apply`.
    */
  private val refIdWrites = (
    (__ \ 'domain).write[String] ~
    (__ \ 'name).write[String] ~
    (__ \ 'value).writeNullable[String] ~
    (__ \ 'value).writeNullable[Seq[String]]
  ).tupled

  /**
    * Validates the JSON representation of a multi-value.
    */
  private val multivalue: Reads[JsArray] = {
    (__ \ 'value).json.pick[JsArray] andThen verifying[JsArray](_.value.nonEmpty)
  }
  
  /**
    * Validates the JSON representation of a [[RefId]].
    *
    * @return A `Reads` that validates the JSON representation of a [[RefId]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateRefId = (
    ((__ \ 'domain).json.pickBranch) ~
    ((__ \ 'name).json.pickBranch) ~
    (((__ \ 'value).json.pickBranch) or ((__ \ 'value).json.copyFrom(multivalue)))
  ).reduce
}
