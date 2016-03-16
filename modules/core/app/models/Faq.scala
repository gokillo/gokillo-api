/*#
  * @file Faq.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import play.api.libs.json._
import models.common.JsModel

/**
  * Represents a frequently asked question.
  *
  * @constructor  Initializes a new instance of the [[Faq]] class.
  * @param json   The faq data as JSON.
  */
class Faq protected(protected var json: JsValue) extends JsModel with api.Faq {

  def question = json as (__ \ 'question).readNullable[String]
  def question_= (v: Option[String]) = setValue((__ \ 'question), Json.toJson(v))
  def answer = json as (__ \ 'answer).readNullable[String]
  def answer_= (v: Option[String]) = setValue((__ \ 'answer), Json.toJson(v))

  def copy(faq: Faq): Faq = new Faq(this.json.as[JsObject] ++ faq.json.as[JsObject])
  def copy(json: JsValue): Faq = Faq(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(faq, _) => faq
    case JsError(_) => new Faq(this.json)
  }
}

/**
  * Factory class for creating [[Faq]] instances.
  */
object Faq {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Faq]] class with the specified JSON.
    *
    * @param json     The faq data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Faq] = {
    validateFaq(relaxed.getOrElse(true)).reads(json).fold(
      valid = { validated => JsSuccess(new Faq(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Faq]] class with the specified values.
    *
    * @param question The frequently asked question.
    * @param answer   The answer to `question`.
    * @return         A new instance of the [[Faq]] class.
    */
  def apply(
    question: Option[String] = None,
    answer: Option[String] = None
  ): Faq = new Faq(
    faqWrites.writes(
      question,
      answer
    )
  ) 

  /**
    * Extracts the content of the specified [[Faq]].
    *
    * @param faq  The [[Faq]] to extract the content from.
    * @return     An `Option` that contains the extracted data,
    *             or `None` if `faq` is `null`.
    */
  def unapply(faq: Faq) = {
    if (faq eq null) None
    else Some((
      faq.question,
      faq.answer
    ))
  }

  /**
    * Serializes/Deserializes a [[Faq]] to/from JSON.
    */
  implicit val faqFormat: Format[Faq] = faqFormat(None)
  def faqFormat(relaxed: Option[Boolean]) = new Format[Faq] {
    def reads(json: JsValue) = Faq(json, relaxed)
    def writes(faq: Faq) = faq.json
  }

  /**
    * Serializes a [[Faq]] to JSON.
    * @note Used internally by `apply`.
    */
  private val faqWrites = (
    (__ \ 'question).writeNullable[String] ~
    (__ \ 'answer).writeNullable[String] 
  ).tupled
  
  /**
    * Validates the JSON representation of a [[Faq]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Faq]].
    * @note           This validator is intended for JSON coming from both inside and
    *                 outside the application.
    */
  def validateFaq(relaxed: Boolean) = (
    ((__ \ 'question).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'answer).json.pickBranch orEmptyIf relaxed)
  ).reduce
}
