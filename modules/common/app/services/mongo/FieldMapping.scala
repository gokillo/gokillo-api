/*#
  * @file FieldMapping.scala
  * @begin 26-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

import scala.util.Try
import play.api.libs.json._
import play.api.libs.json.extensions._
import utils.common.typeExtensions._
import services.common.{FieldMaps, DaoErrors}

/**
  * Provides functionality for converting public fields to their internal
  * representation, and vice versa.
  */
trait FieldMapping {

  private var _defaultMaps: Map[String, (String, Option[String])] = Map(
    "id" -> ("_id", Some("$oid")),
    "pseudoid" -> ("_pseudoid", Some("$oid")),
    "refId.value" -> ("refId.value", Some("$oid")),
    "creationTime" -> ("creationTime", Some("$date"))
  )
  private var _fieldMaps = FieldMaps(_defaultMaps)

  /**
    * Gets the default maps used by `FieldMaps`.
    * @return The default maps.
    */
  protected def defaultMaps = _defaultMaps

  /**
    * Sets the default maps used by `FieldMaps`.
    *
    * @param defaultMaps  The default maps.
    * @note               After the default maps are set, any custom maps are lost
    *                     and need to be set again.
    */
  protected def defaultMaps_=(_maps: Map[String, (String, Option[String])]) = {
    _defaultMaps = _maps
    _fieldMaps = FieldMaps(_defaultMaps)
  }

  /**
    * Gets the field maps from public to internal and vice versa.
    * @return The field maps.
    */
  implicit def fieldMaps = _fieldMaps

  /**
    * Sets the field maps from public to internal.
    *
    * @param maps The field maps.
    * @note       The field maps from internal to public are generated
    *             by [[FieldMaps]] and the default maps are always
    *             included automatically.
    */
  protected def fieldMaps_=(maps: Map[String, (String, Option[String])]) = {
    _fieldMaps = FieldMaps(_defaultMaps ++ maps)
  }

  /**
    * Validates converted fields in the specified JSON, if any.
    *
    * @param json The JSON to validate.
    * @return     `Success`, or `Failure` if `json` contains one or
    *             more invalid field values.
    */
  def validateFields(json: JsValue*): Try[Unit] = Try {
    _fieldMaps.toPublic.keys.foreach { key => key._2 match {
      case Some(key_2) if key_2 == "$oid" =>
        json.foreach { _.getOpt(__ \ key._1 \ key_2).foreach { js =>
          val oid = js.as[JsString].value
          if (!oid.isObjectId) throw DaoErrors.InvalidId(oid, _fieldMaps.toPublic(key))
         }}
      case _ =>
    }}
  }
}
