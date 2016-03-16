/*#
  * @file Pledge.scala
  * @begin 15-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Formats._
import services.core.pledges.fsm
import models.common.{JsEntity, State}
import models.common.State._
import models.pay.Coin
import models.pay.Coin._
import BackerInfo._

/**
  * Represents a pledge to a project.
  *
  * @constructor  Initializes a new instance of the [[Pledge]] class.
  * @param json   The pledge data as JSON.
  */
class Pledge protected(protected var json: JsValue) extends JsEntity with api.Pledge {

  def projectId = json as (__ \ 'projectId).readNullable[String]
  def projectId_= (v: Option[String]) = setValue((__ \ 'projectId), Json.toJson(v))
  def rewardId = json as (__ \ 'rewardId).readNullable[String]
  def rewardId_= (v: Option[String]) = setValue((__ \ 'rewardId), Json.toJson(v))
  def backerInfo = json as (__ \ 'backerInfo).readNullable[BackerInfo]
  def backerInfo_= (v: Option[BackerInfo]) = setValue((__ \ 'backerInfo), Json.toJson(v))
  def amount = json as (__ \ 'amount).readNullable[Coin]
  def amount_= (v: Option[Coin]) = setValue((__ \ 'amount), Json.toJson(v))
  def state = json as (__ \ 'state).readNullable[State]
  def state_= (v: Option[State]) = setValue((__ \ 'state), Json.toJson(v))

  def copy(pledge: Pledge): Pledge = new Pledge(this.json.as[JsObject] ++ pledge.json.as[JsObject])
  def copy(json: JsValue): Pledge = Pledge(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(pledge, _) => pledge
    case JsError(_) => new Pledge(this.json)
  }
}

/**
  * Factory class for creating [[Pledge]] instances.
  */
object Pledge {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Pledge]] class with the specified JSON.
    *
    * @param json The pledge data as JSON.
    * @return     A `JsResult` value that contains the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Pledge] = {
    validatePledge.reads(json).fold(
      valid = { validated => JsSuccess(new Pledge(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Pledge]] class with the specified values.
    *
    * @param id           The identifier of the pledge.
    * @param projectId    The identifier of the project to fund.
    * @param rewardId     The identifier of the reward this pledge is associated with.
    * @param backerInfo   Information about the backer that pledged to the project.
    * @param amount       The amount of the pledge.
    * @param state        One of the [[services.core.pledges.PledgeFsm]] values.
    * @param creationTime The time the pledge was created.
    * @return             A new instance of the [[Pledge]] class.
    */
  def apply(
    id: Option[String] = None,
    projectId: Option[String] = None,
    rewardId: Option[String] = None,
    backerInfo: Option[BackerInfo] = None,
    amount: Option[Coin] = None,
    state: Option[State] = None,
    creationTime: Option[DateTime] = None
  ): Pledge = new Pledge(
    pledgeWrites.writes(
      id,
      projectId,
      rewardId,
      backerInfo,
      amount,
      state,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Pledge]].
    *
    * @param pledge The [[Pledge]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `pledge` is `null`.
    */
  def unapply(pledge: Pledge) = {
    if (pledge eq null) None
    else Some((
      pledge.id,
      pledge.projectId,
      pledge.rewardId,
      pledge.backerInfo,
      pledge.amount,
      pledge.state,
      pledge.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Pledge]] to/from JSON.
    */
  implicit val pledgeFormat = new Format[Pledge] {
    def reads(json: JsValue) = Pledge(json)
    def writes(pledge: Pledge) = pledge.json
  }

  /**
    * Serializes a [[Pledge]] to JSON.
    * @note Used internally by `apply`.
    */
  private val pledgeWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'projectId).writeNullable[String] ~
    (__ \ 'rewardId).writeNullable[String] ~
    (__ \ 'backerInfo).writeNullable[BackerInfo] ~
    (__ \ 'amount).writeNullable[Coin] ~
    (__ \ 'state).writeNullable[State] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of the backer information.
    */
  private val backerInfo = (__ \ 'backerInfo).json.pick[JsValue] andThen validateBackerInfo

  /**
    * Validates the JSON representation of a monetary value.
    */
  private val amount = (__ \ 'amount).json.pick[JsValue] andThen validateCoin

  /**
    * Validates the JSON representation of the pledge state.
    */
  private val state = (__ \ 'state).json.pick[JsValue] andThen validateState

  /**
    * Validates the JSON representation of a [[Pledge]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Pledge]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validatePledge = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'projectId).json.pickBranch orEmpty) ~
    ((__ \ 'rewardId).json.pickBranch orEmpty) ~
    ((__ \ 'backerInfo).json.copyFrom(backerInfo) orEmpty) ~
    ((__ \ 'amount).json.copyFrom(amount) orEmpty) ~
    ((__ \ 'state).json.copyFrom(state) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce
}
