/*#
  * @file Comment.scala
  * @begin 4-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

import play.api.libs.json._

/**
  * Represents a user comment about an action.
  *
  * @constructor  Initializes a new instance of the [[Comment]] class.
  * @param json   The comment data as JSON.
  */
class Comment protected(protected var json: JsValue) extends JsModel with api.Comment {

  def text = json as (__ \ 'text).read[String]
  def text_= (v: String) = setValue((__ \ 'text), Json.toJson(v))

  def copy(comment: Comment): Comment = new Comment(this.json.as[JsObject] ++ comment.json.as[JsObject])
  def copy(json: JsValue): Comment = Comment(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(comment, _) => comment
    case JsError(_) => new Comment(this.json)
  }
}

/**
  * Factory class for creating [[Comment]] instances.
  */
object Comment {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._

  private val MinCommentLength = configuration.getInt("common.minCommentLength").getOrElse(10)

  /**
    * Initializes a new instance of the [[Comment]] class with the specified JSON.
    *
    * @param json The comment data as JSON.
    * @return     A `JsResult` value that contains the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Comment] = {
    validateComment.reads(json).fold(
      valid = { validated => JsSuccess(new Comment(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Comment]] class with the specified values.
    *
    * @param text   The text of the comment.
    * @return       A new instance of the [[Comment]] class.
    */
  def apply(text: String): Comment = new Comment(
    commentWrites.writes(text)
  ) 

  /**
    * Extracts the content of the specified [[Comment]].
    *
    * @param comment  The [[Comment]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `comment` is `null`.
    */
  def unapply(comment: Comment) = {
    if (comment eq null) None
    else Some((comment.text))
  }

  /**
    * Serializes/Deserializes a [[Comment]] to/from JSON.
    */
  implicit val commentFormat = new Format[Comment] {
    def reads(json: JsValue) = Comment(json)
    def writes(comment: Comment) = comment.json
  }

  /**
    * Serializes a [[Comment]] to JSON.
    * @note Used internally by `apply`.
    */
  private val commentWrites = (
    (__ \ 'text).write[String]
  )
  
  /**
    * Validates the JSON representation of a [[Comment]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Comment]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  def validateComment = (
    ((__ \ 'text).json.pickBranch(Reads.of[JsString] <~ Reads.minLength[String](MinCommentLength)))
  )
}
