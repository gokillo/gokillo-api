/*#
  * @file MetaFile.scala
  * @begin 11-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._

/**
  * Describes a file in a ''File Store''.
  *
  * @constructor  Initializes a new instance of the [[MetaFile]] class.
  * @param json   The meta-file data as JSON.
  */
class MetaFile protected(protected var json: JsValue) extends JsModel with api.MetaFile {

  def id = json as (__ \ 'id).read[String]
  def contentType = json as (__ \ 'contentType).readNullable[String]
  def filename = json as (__ \ 'filename).read[String]
  def uploadDate = json as (__ \ 'uploadDate).readNullable[DateTime]
  def uploadDateMillis = uploadDate.map(_.getMillis)
  def chunkSize = json as (__ \ 'chunkSize).read[Int]
  def length = json as (__ \ 'length).read[Int]
  def md5 = json as (__ \ 'md5).readNullable[String]
  def metadata = json as (__ \ 'metadata).readNullable[JsValue]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[MetaFile]] instances.
  */
object MetaFile {

  import play.api.libs.functional.syntax._

  /**
    * Initializes a new instance of the [[MetaFile]] class with the specified JSON.
    *
    * @param json The meta-file data as JSON.
    * @return     A new instance of the [[MetaFile]] class.
    */
  def apply(json: JsValue): MetaFile = new MetaFile(json)

 /**
   * Extracts the content of the specified [[MetaFile]].
   *
   * @param metaFile  The [[MetaFile]] to extract the content from.
   * @return          An `Option` that contains the extracted data,
   *                  or `None` if `metaFile` is `null`.
   */
  def unapply(metaFile: MetaFile) = {
    if (metaFile eq null) null
    else Some((
      metaFile.id,
      metaFile.contentType,
      metaFile.filename,
      metaFile.uploadDate,
      metaFile.chunkSize,
      metaFile.length,
      metaFile.md5,
      metaFile.metadata
    ))
  }

  /**
    * Serializes a [[MetaFile]] to JSON.
    */
  implicit val metaFileWrites = new Writes[MetaFile] {
    def writes(metaFile: MetaFile) = metaFile.json
  }
}
