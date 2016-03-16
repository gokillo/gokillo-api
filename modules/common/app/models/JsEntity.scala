/*#
  * @file JsEntity.scala
  * @begin 14-Jan-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._

/**
  * Represents an entity whose internal representation is JSON.
  *
  * A `JsEntity` is just a `JsModel` with getter and setter for
  * the `id`.
  */
trait JsEntity extends JsModel {

  protected var json: JsValue

  /**
    * Gets the entity id.
    * @return An `Option` value containing the entity id,
    *         or `None` if undefined.
    */
  def id = json as (__ \ 'id).readNullable[String]

  /**
    * Sets the entity id.
    * @param v  An `Option` that contains the entity id,
    *           or `None` if undefined.
    */
  def id_= (v: Option[String]) = setValue((__ \ 'id), Json.toJson(v))

  /**
    * Gets the entity pseudoid.
    * @return An `Option` value containing the entity pseudoid,
    *         or `id` if undefined.
    */
  def pseudoid = json as (__ \ 'pseudoid).readNullable[String] orElse id

  /**
    * Sets the entity pseudoid.
    * @param v  An `Option` that contains the entity pseudoid,
    *           or `None` if undefined.
    */
  def pseudoid_= (v: Option[String]) = setValue((__ \ 'pseudoid), Json.toJson(v))

  /**
    * Gets the creation time.
    * @return An `Option` value containing the creation time,
    *         or `None` if undefined.
    */
  def creationTime = json as (__ \ 'creationTime).readNullable[DateTime]

  /**
    * Sets the creation time.
    * @param v  An `Option` that contains the creation time,
    *           or `None` if undefined.
    */
  def creationTime_= (v: Option[DateTime]) = setValue((__ \ 'creationTime), Json.toJson(v))
}
