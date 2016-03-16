/*#
  * @file MetaFile.scala
  * @begin 24-Jun-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common.api

import com.wordnik.swagger.annotations._
import org.joda.time.DateTime

/**
  * Defines the `MetaFile` schema for Swagger.
  */
@ApiModel(value = "MetaFile", description = "Describes a file in a File Store")
trait MetaFile {

  @ApiModelProperty(value = "The identifier of the file", dataType = "string", /* readOnly = true, */ position = 1)
  def id: String
  @ApiModelProperty(value = "The content type of the file", dataType = "string", /* readOnly = true, */ position = 2)
  def contentType: Option[String]
  @ApiModelProperty(value = "The name of the file", dataType = "string", /* readOnly = true, */ position = 3)
  def filename: String
  @ApiModelProperty(value = "The date the file was uploaded in ISO 8601 format", dataType = "datetime", /* readOnly = true, */ position = 4)
  def uploadDate: Option[DateTime]
  @ApiModelProperty(value = "The size of the file chunks", dataType = "int", /* readOnly = true, */ position = 5)
  def chunkSize: Int
  @ApiModelProperty(value = "The length of the file, in bytes", dataType = "int", /* readOnly = true, */ position = 6)
  def length: Int
  @ApiModelProperty(value = "The checksum of the file", dataType = "string", /* readOnly = true, */ position = 7)
  def md5: Option[String]
}
