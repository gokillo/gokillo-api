/*#
  * @file TokenTrace.scala
  * @begin 24-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._
import models.common.JsEntity

/**
  * Represents a token trace.
  *
  * @constructor  Initializes a new instance of the [[TokenTrace]] class.
  * @param json   The token trace data as JSON.
  */
class TokenTrace private(protected var json: JsValue) extends JsEntity with Serializable {

  def username = json as (__ \ 'username).readNullable[String]
  def username_= (v: Option[String]) = setValue((__ \ 'username), Json.toJson(v))
  def expirationTime = json as (__ \ 'expirationTime).read[DateTime]
  def expirationTime_= (v: DateTime) = setValue((__ \ 'expirationTime), Json.toJson(v))

  def copy(json: JsValue) = throw new UnsupportedOperationException

  override def toString = json.toString
}

/**
  * Factory class for creating [[TokenTrace]] instances.
  */
object TokenTrace {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[TokenTrace]] class with the specified values.
    *
    * @param id             The id of the token to trace.
    * @param username       The username associated with the token to trace.
    * @param expirationTime The time the token to trace expires.
    * @param creationTime   The time the token to trace was created.
    * @return               A new instance of the `TokenTrace` class.
    */
  def apply(
    id: String,
    username: Option[String],
    expirationTime: DateTime,
    creationTime: Option[DateTime] = None
  ) = new TokenTrace(
    tokenTraceWrites.writes(
      Some(id),
      username,
      expirationTime,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[TokenTrace]].
    *
    * @param tokenTrace The [[TokenTrace]] to extract the content from.
    * @return           An `Option` that contains the extracted data,
    *                   or `None` if `tokenTrace` is `null`.
    */
  def unapply(tokenTrace: TokenTrace) = {
    if (tokenTrace eq null) None
    else Some((
      tokenTrace.id,
      tokenTrace.username,
      tokenTrace.expirationTime,
      tokenTrace.creationTime
    ))
  }

  /**
    * Serializes a [[TokenTrace]] to JSON.
    * @note Used internally by `apply`.
    */
  private val tokenTraceWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'username).writeNullable[String] ~
    (__ \ 'expirationTime).write[DateTime] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled
}
