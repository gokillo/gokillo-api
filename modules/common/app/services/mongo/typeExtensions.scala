/*
  * @file typeExtensions.scala
  * @begin 8-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

/**
  * Extends a set of types with custom functionality for Mongo.
  */
package object typeExtensions {

  import scala.util.matching.Regex
  import org.joda.time.{DateTime, DateTimeZone, LocalDate}
  import org.joda.time.format.ISODateTimeFormat
  import play.api.libs.json._
  import play.api.libs.json.extensions._
  import reactivemongo.bson.{BSONObjectID, BSONDocument}
  import services.common.FieldMaps

  private val PathPattern = """^\/?(.*?)(?=(?:\(\d*\))?$)""".r

  private[this] def fullyQualifiedKey(path: JsPath, key: String) = {
    PathPattern.findFirstMatchIn(path.toString).map(m => m.group(1)) match {
      case Some(p) if p.length > 0 => s"""${p.replaceAll("/", ".")}.$key"""
      case _ => key
    }
  }

  private[this] def repath(internalKey: String, publicKey: String) = {
    val tagCount = publicKey.count(_ == '.')
    if (tagCount == internalKey.count(_ == '.')) internalKey
    else {
      val tags = internalKey.split("\\.")
      tags.drop(tags.length - (tagCount + 1)).mkString(".")
    }
  }

  private[this] def fromPublicWithMaps(json: JsValue, fieldMaps: FieldMaps): JsValue = {
    def fromPublic(json: JsValue, public: String, internal: (String, Option[String])) = {
      def fromPublic(path: JsPath, key: String, value: String) = repath(internal._1, key) -> internal._2.map { `type` =>
        val valueType = if (`type` == "$localdate") "$date" else `type`
        Json.obj(valueType -> (valueType match {
          case "$date" => new DateTime(value, DateTimeZone.UTC).getMillis
          case _ => value
        }))
      }.getOrElse(Json.obj(internal._1 -> json))

      json.updateAllKeyNodes {
        case ((path \ key), JsString(value))  if fullyQualifiedKey(path, key) == public => fromPublic(path, key, value)
        case ((path \ key), JsArray(value))   if fullyQualifiedKey(path, key) == public => (
          key, JsArray(for (e <- value) yield { fromPublic(path, key, e.as[JsString].value)._2 })
        )
      }
    }

    fieldMaps.fromPublic.foldLeft(json) {
      case (js, (public, internal)) => fromPublic(js, public, internal)
    }
  }

  private[this] def toPublicWithMaps(json: JsValue, fieldMaps: FieldMaps): JsValue = {
    def keyOrValue(fullyQualifiedKey: String, valueType: Option[String], json: JsValue): (String, JsValue) = {
      def keyOrValue(key: String, value: JsValue) = value match {
          case JsUndefined() => key -> json
          case value => key -> (valueType.get match {
            case "$date" => value.validate[Long].fold(
              invalid => value,
              valid => JsString(ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC).print(new DateTime(valid)))
            )
            case "$localdate" => value.validate[Long].fold(
              invalid => value,
              valid => JsString(new LocalDate(valid).toString)
            )
            case _ => value
          })
      }

      val key = fullyQualifiedKey.substring(fullyQualifiedKey.lastIndexOf(".") + 1)
      valueType.map { `type` =>
        val internalValueType = if (`type` == "$localdate") "$date" else `type`
        json match {
          case arr: JsArray => (
            key, JsArray(for (e <- arr.as[Seq[JsObject]]) yield { keyOrValue(key, e \ internalValueType)._2 })
          )
          case _ => keyOrValue(key, json \ internalValueType)
        }
      }.getOrElse(key -> json)
    }

    def toPublic(json: JsValue, internal: (String, Option[String]), public: String) = json.updateAllKeyNodes {
      case ((path \ key), value) if fullyQualifiedKey(path, key) == internal._1 => keyOrValue(public, internal._2, value)
    }

    fieldMaps.toPublic.foldLeft(json) {
      case (js, (internal, public)) => toPublic(js, internal, public)
    }
  }

  /**
    * Extends `JsValue` instances with custom functionality for Mongo.
    *
    * @constructor  Initialized a new instance of the [[JsValueWithMongoExtensions]] class.
    * @param json   The `JsValue` instance to extend with custom functionality.
    */
  implicit class JsValueWithMongoExtensions(val json: JsValue) extends AnyVal {

    import scala.collection.mutable.Buffer
    import play.modules.reactivemongo.json.ImplicitBSONHandlers.JsObjectWriter
    import utils.common.typeExtensions._

    /**
      * Converts a `JsValue` to BSON.
      * @return The BSON converted from the current JSON.
      */
    def toBson = JsObjectWriter.write(json.as[JsObject])

    /**
      * Converts a `JsValue` into an operator object according to the specified
      * field and value pairs.
      *
      * @param fieldMaps  A `Map` that maps internal fields to public fields.
      * @param pairs      The key and value pairs.
      * @return           An operator object if at least one key in `pairs` matches a
      *                   field in the current JSON; otherwise, the original JSON.
      */
    def toOperator(fieldMaps: FieldMaps, pairs: (String, JsValue)*): JsValue = {
      var js = json
      val seq = Buffer[(String, JsValue)]()

      for (pair <- pairs) { js.getOpt(__ \ pair._1).foreach { value =>
        pair._1 match {
          case "$like" =>
            js = js.delete(__ \ pair._1)
            val obj = value.as[JsObject]
            for (key <- obj.keys) {
              seq += fieldMaps.fromPublic(key)._1 -> Json.obj(
                "$regex" -> s".*${(obj \ key).as[JsString].value}.*", "$options" -> "i"
              )
            }
          case "$include" | "$exclude" | "$asc" | "$desc" =>
            js = js.delete(__ \ pair._1)
            for (field <- value.as[List[JsString]])  {
              seq += fieldMaps.fromPublic(field.value)._1 -> pair._2
            }
          case "$and" | "$or" | "$nor" =>
            js = js.delete(__ \ pair._1)
            seq += pair._1 -> Json.toJson { for {
              field <- value.as[List[JsObject]]
              key <- field.keys
            } yield {
              val fromPublic = fieldMaps.fromPublic(key); val v = field \ key
              Json.obj(fromPublic._1 -> fromPublic._2.map(t => Json.obj(t -> v)).getOrElse(v).as[JsValue])
            }}
          case "$in" | "$eq" | "$ne" | "$gt" | "$lt" | "$gte" | "$lte" =>
            js = js.delete(__ \ pair._1)
            val obj = value.as[JsObject]
            for (key <- obj.keys) {
              val fromPublic = fieldMaps.fromPublic(key); val v = obj \ key
              seq += fromPublic._1 -> Json.obj(pair._1 -> fromPublic._2.map { t =>
                pair._1 match {
                  case "$in" => JsArray(for (e <- v.as[List[JsValue]]) yield Json.obj(t -> e))
                  case _ => Json.obj(t -> v)
                }}.getOrElse(v).as[JsValue]
              )
            }
          case "$getItem" =>
            js = js.delete(__ \ pair._1)
            val obj = value.as[JsObject]
            for (key <- obj.keys) {
              seq += fieldMaps.fromPublic(key)._1 -> Json.obj("$slice" -> Json.arr(obj \ key, 1))
            }
          case "$matchItem" =>
            js = js.delete(__ \ pair._1)
            val obj = value.as[JsObject]
            for (key <- obj.keys) { obj.getOpt(__ \ key).foreach { matchObj =>
              for (wrappedMatchKey <- matchObj.as[JsArray].value) {
                val matchKey = wrappedMatchKey.as[JsString].value
                matchObj.getOpt(__ \ matchKey).foreach { items =>
                val fields = items.as[Seq[JsValue]].map(item => (matchKey -> item))
                seq += fieldMaps.fromPublic(key)._1 -> Json.obj("$elemMatch" -> JsObject(fields))
              }}
            }}
        }
      }}

      // merge remaining json with operator object
      js = js.fromPublic()(fieldMaps)
      if (seq.length > 0) JsObject(seq) ++ js.asOpt[JsObject].getOrElse(Json.obj()) else js
    }

    /**
      * Converts a `JsValue` into an internal JSON representation to be used
      * to interact with Mongo.
      *
      * @param fieldMaps  A `Map` that maps public fields to internal fields.
      * @return           An internal JSON representation.
      */
    def fromPublic()(implicit fieldMaps: FieldMaps) = fromPublicWithMaps(json, fieldMaps)

    /**
      * Converts a `JsValue` into a public JSON representation to be used
      * to interact with the rest of the world.
      *
      * @param fieldMaps  A `Map` that maps internal fields to public fields.
      * @return           A public JSON representation.
      */
    def toPublic()(implicit fieldMaps: FieldMaps) = {
      toPublicWithMaps(json, fieldMaps)
      // val public = toPublicWithMaps(json, fieldMaps)
      // public.getOpt(__ \ '_version).map(_ => public.delete(__ \ '_version)).getOrElse(public)
    }

    /**
      * Converts a `JsValue` into an update object.
      * @return An update object.
      */
    def toUpdate()(implicit fieldMaps: FieldMaps) = {
      val (withValue, withoutValue) = json.as[JsObject].fields.partition(_._2.hasValue)

      val set = if (withValue.length > 0) JsExtensions.buildJsObject(
        __ \ '$set -> fromPublicWithMaps(JsObject(withValue), fieldMaps)
      ).as[JsObject] else Json.obj()

      val unset = if (withoutValue.length > 0) JsExtensions.buildJsObject(
        __ \ '$unset -> fromPublicWithMaps(JsObject(withoutValue), fieldMaps)
      ).as[JsObject] else Json.obj()

      set ++ unset
    }

    /**
      * Converts a `JsValue` into a projection object.
      *
      * @param fieldMaps  A `Map` that maps internal fields to public fields.
      * @return           A projection object.
      */
    def toProjection()(implicit fieldMaps: FieldMaps) = toOperator(fieldMaps,
      ("$include", JsNumber(1)),
      ("$exclude", JsNumber(0)),
      ("$getItem", JsNull),
      ("$matchItem", JsNull)
    )

    /**
      * Converts a `JsValue` into a sorting object.
      *
      * @param fieldMaps  A `Map` that maps internal fields to public fields.
      * @return           A sorting object.
      */
    def toSort()(implicit fieldMaps: FieldMaps) = toOperator(fieldMaps,
      ("$asc", JsNumber(1)),
      ("$desc", JsNumber(-1))
    )

    /**
      * Converts a `JsValue` into a selector object with pattern matching
      * capability.
      *
      * @param fieldMaps  A `Map` that maps internal fields to public fields.
      * @return           A selector object.
      */
    def toSelector()(implicit fieldMaps: FieldMaps) = toOperator(fieldMaps,
      ("$like", JsNull),
      ("$and", JsNull),
      ("$or", JsNull),
      ("$nor", JsNull),
      ("$in", JsNull),
      ("$ne", JsNull),
      ("$gt", JsNull),
      ("$lt", JsNull),
      ("$gte", JsNull),
      ("$lte", JsNull)
    )

    /**
      * Sets the specified version in a `JsValue`.
      *
      * @param version  An `Option` value containing the version to set,
      *                 or `None` to unset possible version information.
      * @return         A JSON value with version information.
      */
    def setVersion(version: Option[Int]) = version match {
      case Some(v) => json.set(__ \ '_version -> JsNumber(v))
      case None => json.getOpt(__ \ '_version).map(_ => json.delete(__ \ '_version)).getOrElse(json)
    }

    /**
      * Converts a `JsValue` into a versioned document.
      *
      * @param removed  A Boolean value indicating whether or not the document
      *                 has been removed.
      * @return         A versioned document.
      */
    def toVersioned(removed: Boolean) = {
      import org.joda.time.{DateTime, DateTimeZone}
      import utils.common.Formats._

      json.getOpt(__ \ '_id \ '$oid).map { id =>
        json.getOpt(__ \ '_version).map { version =>
          json.delete(__ \ '_id \ '$oid).set(
            (__ \ '_id \ '_id \ '$oid) -> id,
            (__ \ '_id \ '_version) -> version,
            (__ \ '_version) -> (version match {
              case JsNumber(v) => if (removed) JsString(f"deleted:$v%.0f") else JsNumber(v)
              case _ => JsNull
            }),
            (__ \ '_timestamp \ '$date) -> Json.toJson(DateTime.now(DateTimeZone.UTC).getMillis)
          )
        }.getOrElse(json)
      }.getOrElse(json)
    }

    /**
      * Adds an object id to a `JsValue`.
      * @return A JSON value identified by a new object id.
      */
    def withObjectId = json.getOpt(__ \ '_id \ '$oid).map(_ => json).getOrElse(
      json.set(__ \ '_id \ '$oid -> JsString(BSONObjectID.generate.stringify))
    )
  }

  /**
    * Extends `BSONDocument` instances with custom functionality for Mongo.
    *
    * @constructor  Initialized a new instance of the [[BSONDocumentWithMongoExtensions]] class.
    * @param bson   The `BSONDocument` instance to extend with custom functionality.
    */
  implicit class BSONDocumentWithMongoExtensions(val bson: BSONDocument) extends AnyVal {

    import play.modules.reactivemongo.json.ImplicitBSONHandlers.JsObjectReader

    /**
      * Converts a `BSONDocument` to JSON.
      * @return The JSON converted from the current BSON.
      */
    def toJson = JsObjectReader.read(bson)
  }

  /**
    * Extends `String` instances with custom functionality for Mongo.
    *
    * @constructor  Initialized a new instance of the [[StringWithMongoExtensions]] class.
    * @param string The `String` instance to extend with custom functionality.
    */
  implicit class StringWithMongoExtensions(val string: String) extends AnyVal {

    /**
      * Converts the current string into an object id.
      *
      * @param  key The name of the object id, default to `_id`.
      * @return The object id.
      */
    def toObjectId: JsValue = toObjectId("_id")
    def toObjectId(key: String): JsValue = {
      Writes[String] { id => Json.obj(key -> Json.obj("$oid" -> id)) }.writes(string)
    }

    /**
      * Extracts the Mongo error field from the current string,
      * if any.
      *
      * @return An `Option` value containing the error field, or `None`
      *         if not found.
      */
    def errorField: Option[String] = {
      """(?<=\$)[^}]*(?=_)""".r.findFirstIn(string) match {
        case Some(field) => Some(field.stripPrefix("_"))
        case _ => None
      }
    }
  }
}
