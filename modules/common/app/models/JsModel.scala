/*#
  * @file JsModel.scala
  * @begin 14-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._

/**
  * Represents a model whose internal representation is JSON.
  */
trait JsModel {

  /**
    * The internal JSON representation of this object.
    */
  protected var json: JsValue

  /**
    * Sets the value of the specified key in the internal JSON
    * representation.
    *
    * @param key    The key to set the value for.
    * @param value  The new value of `key`.
    */
  protected def setValue(key: JsPath, value: JsValue) = {
    value match {
      case JsNull => json.transform(key.json.prune).map(t => json = t)
      case _ => json.transform((__.json.update(key.json.put(value)))).map(t => json = t)
    }
  }

  /**
    * Gets the internal JSON representation of this object.
    * @return The internal JSON representation of this object.
    */
  def asJson = json

  /**
    * Gets the object version.
    * @return An `Option` value containing the object version,
    *         or `None` if undefined.
    */
  def version = json as (__ \ '_version).readNullable[Int]

  /**
    * Sets the object version.
    * @param v  An `Option` that contains the object version,
    *           or `None` if undefined.
    */
  def version_= (v: Option[Int]) = setValue((__ \ '_version), Json.toJson(v))

  /**
    * Creates a new copy of this [[JsModel]] with the specified JSON.
    *
    * @param json The JSON to be merged into this [[JsModel]].
    * @return     The new copy of this [[JsModel]].
    */
  def copy(json: JsValue): JsModel

  /**
    * Compares the specified object with this instance for equality.
    *
    * @param obj  The object to compare with this instance.
    * @return     `true` if `obj` is equal to this instance; otherwise, `false`.
    */
  override def equals(obj: Any) = obj match {
    case that: JsModel => that.json.equals(this.json)
    case _ => false
  }

  /**
    * Returns the hash code value for this object.
    *
    * @return The hash code value for this object.
    */
  override def hashCode = json.hashCode
}
