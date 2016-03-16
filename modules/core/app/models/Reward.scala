/*#
  * @file Reward.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import org.joda.time.LocalDate
import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents a reward granted to a backer.
  *
  * @constructor  Initializes a new instance of the [[Reward]] class.
  * @param json   The reward data as JSON.
  */
class Reward protected(protected var json: JsValue) extends JsModel with api.Reward {

  def id = json as (__ \ 'id).readNullable[String]
  def id_= (v: Option[String]) = setValue((__ \ 'id), Json.toJson(v))
  def pledgeAmount = json as (__ \ 'pledgeAmount).readNullable[Double]
  def pledgeAmount_= (v: Option[Double]) = setValue((__ \ 'pledgeAmount), Json.toJson(v))
  def description = json as (__ \ 'description).readNullable[String]
  def description_= (v: Option[String]) = setValue((__ \ 'description), Json.toJson(v))
  def estimatedDeliveryDate = json as (__ \ 'estimatedDeliveryDate).readNullable[LocalDate]
  def estimatedDeliveryDate_= (v: Option[LocalDate]) = setValue((__ \ 'estimatedDeliveryDate), Json.toJson(v))
  def shipping = json as (__ \ 'shipping).readNullable[String]
  def shipping_= (v: Option[String]) = setValue((__ \ 'shipping), Json.toJson(v))
  def selectCount = json as (__ \ 'selectCount).readNullable[Int]
  def selectCount_= (v: Option[Int]) = setValue((__ \ 'selectCount), Json.toJson(v))
  def availableCount = json as (__ \ 'availableCount).readNullable[Int]
  def availableCount_= (v: Option[Int]) = setValue((__ \ 'availableCount), Json.toJson(v))

  def copy(reward: Reward): Reward = new Reward(this.json.as[JsObject] ++ reward.json.as[JsObject])
  def copy(json: JsValue): Reward = Reward(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(reward, _) => reward
    case JsError(_) => new Reward(this.json)
  }
}

/**
  * Factory class for creating [[Reward]] instances.
  */
object Reward extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Reward]] class with the specified JSON.
    *
    * @param json     The reward data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Reward] = {
    { relaxed match {
      case Some(r) => validateReward(r)
      case _ => validateReward
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new Reward(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Reward]] class with the specified values.
    *
    * @param pledgeAmount   The amount that entitles a backer to receive
    *                       this reward.
    * @param description    The description of the reward.
    * @param estimatedDeliveryDate The estimated delivery date of the reward.
    * @param shipping       How the reward will be delivered.
    * @param selectCount    The number of times the reward has been selected.
    * @param availableCount The number of reward instances still available.
    * @return               A new instance of the [[Reward]] class.
    */
  def apply(
    id: Option[String] = None,
    pledgeAmount: Option[Double] = None,
    description: Option[String] = None,
    estimatedDeliveryDate: Option[LocalDate] = None,
    shipping: Option[String] = None,
    selectCount: Option[Int] = None,
    availableCount: Option[Int] = None
  ): Reward = new Reward(
    rewardWrites.writes(
      id,
      pledgeAmount,
      description,
      estimatedDeliveryDate,
      shipping,
      selectCount,
      availableCount
    )
  )

  /**
    * Extracts the content of the specified [[Reward]].
    *
    * @param reward The [[Reward]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `reward` is `null`.
    */
  def unapply(reward: Reward) = {
    if (reward eq null) None
    else Some((
      reward.id,
      reward.pledgeAmount,
      reward.description,
      reward.estimatedDeliveryDate,
      reward.shipping,
      reward.selectCount,
      reward.availableCount
    ))
  }

  /**
    * Serializes/Deserializes a [[Reward]] to/from JSON.
    */
  implicit val rewardFormat: Format[Reward] = rewardFormat(None)
  def rewardFormat(relaxed: Option[Boolean]) = new Format[Reward] {
    def reads(json: JsValue) = Reward(json, relaxed)
    def writes(reward: Reward) = reward.json
  }

  /**
    * Serializes a [[Reward]] to JSON.
    * @note Used internally by `apply`.
    */
  private val rewardWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'pledgeAmount).writeNullable[Double] ~
    (__ \ 'description).writeNullable[String] ~
    (__ \ 'estimatedDeliveryDate).writeNullable[LocalDate] ~
    (__ \ 'shipping).writeNullable[String] ~
    (__ \ 'selectCount).writeNullable[Int] ~
    (__ \ 'availableCount).writeNullable[Int]
  ).tupled

  /**
    * Validates the JSON representation of a [[Reward]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Reward]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateReward = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'pledgeAmount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'description).json.pickBranch orEmpty) ~
    ((__ \ 'estimatedDeliveryDate).json.pickBranch(Reads.of[JsString] <~ isoDate) orEmpty) ~
    ((__ \ 'shipping).json.pickBranch orEmpty) ~
    ((__ \ 'selectCount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'availableCount).json.pickBranch(Reads.of[JsNumber]) orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[Reward]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Reward]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateReward(relaxed: Boolean) = (
    ((__ \ 'pledgeAmount).json.pickBranch(Reads.of[JsNumber]) orEmptyIf relaxed) ~
    ((__ \ 'description).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'estimatedDeliveryDate).json.pickBranch(Reads.of[JsString] <~ isoDate) orEmptyIf relaxed) ~
    ((__ \ 'shipping).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'availableCount).json.pickBranch(Reads.of[JsNumber]) orEmpty)
  ).reduce
}
