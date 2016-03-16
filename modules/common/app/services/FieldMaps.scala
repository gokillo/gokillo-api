/*
  * @file FieldMaps.scala
  * @begin 16-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import scala.collection.immutable.Map

/**
  * Provides mappings between public and internal fields, and vice versa.
  *
  * @constructor      Initializes a new instance of the [[FieldMaps]] class.
  * @param fromPublic A `Map` that maps public fields to internal fields.
  * @param toPublic   A `Map` that maps internal fields to public fields.
  */
class FieldMaps private(
  val fromPublic: Map[String, (String, Option[String])],
  val toPublic: Map[(String, Option[String]), String]
){}

/**
  * Factory class for creating [[FieldMaps]] instances.
  */
object FieldMaps {

  /**
    * Initializes a new instance of the [[FieldMaps]] class.
    *
    * @param fields A `Map` that maps public fields to internal fields.
    * @return       A new [[FieldMaps]] instance.
    */
  def apply(fields: Map[String, (String, Option[String])]) = {
    new FieldMaps(
      fields.withDefault(key => (key, None)),
      fields.map(_.swap).withDefault(key => key._1)
    )
  }
}
