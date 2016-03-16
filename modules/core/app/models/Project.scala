/*#
  * @file Project.scala
  * @begin 12-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import org.joda.time.DateTime
import play.api.libs.json._
import services.core.projects.fsm
import utils.common.Formats._
import models.common.{JsEntity, HistoryEvent, State}
import models.common.HistoryEvent._
import models.common.State._
import models.auth.IdentityMode._

import FundingInfo._
import Reward._
import Faq._

/**
  * Represents a project.
  *
  * @constructor  Initializes a new instance of the [[Project]] class.
  * @param json   The project data as JSON.
  */
class Project protected(protected var json: JsValue) extends JsEntity with api.Project {

  def accountId = json as (__ \ 'accountId).readNullable[String]
  def accountId_= (v: Option[String]) = setValue((__ \ 'accountId), Json.toJson(v))
  def ownerIdentityMode = json as (__ \ 'ownerIdentityMode).readNullable[IdentityMode]
  def ownerIdentityMode_= (v: Option[IdentityMode]) = setValue((__ \ 'ownerIdentityMode), Json.toJson(v))
  def name = json as (__ \ 'name).readNullable[String]
  def name_= (v: Option[String]) = setValue((__ \ 'name), Json.toJson(v))
  def categories = json as (__ \ 'categories).readNullable[List[String]]
  def categories_= (v: Option[List[String]]) = setValue((__ \ 'categories), Json.toJson(v))
  def blurb = json as (__ \ 'blurb).readNullable[String]
  def blurb_= (v: Option[String]) = setValue((__ \ 'blurb), Json.toJson(v))
  def description = json as (__ \ 'description).readNullable[String]
  def description_= (v: Option[String]) = setValue((__ \ 'description), Json.toJson(v))
  def location = json as (__ \ 'location).readNullable[String]
  def location_= (v: Option[String]) = setValue((__ \ 'location), Json.toJson(v))
  def pickedOpt = json as (__ \ 'picked).readNullable[Boolean]
  def pickedOpt_= (v: Option[Boolean]) = setValue((__ \ 'picked), Json.toJson(v))
  def picked = pickedOpt.getOrElse(false)
  def picked_= (v: Boolean) = pickedOpt_=(Some(v))
  def state = json as (__ \ 'state).readNullable[State]
  def state_= (v: Option[State]) = setValue((__ \ 'state), Json.toJson(v))
  def fundingInfo = json as (__ \ 'fundingInfo).readNullable[FundingInfo]
  def fundingInfo_= (v: Option[FundingInfo]) = setValue((__ \ 'fundingInfo), Json.toJson(v))
  def rewards = json as (__ \ 'rewards).readNullable[List[Reward]]
  def rewards_= (v: Option[List[Reward]]) = setValue((__ \ 'rewards), Json.toJson(v))
  def faqs = json as (__ \ 'faqs).readNullable[List[Faq]]
  def faqs_= (v: Option[List[Faq]]) = setValue((__ \ 'faqs), Json.toJson(v))
  def history = json as (__ \ 'history).readNullable[List[HistoryEvent]]
  def history_= (v: Option[List[HistoryEvent]]) = setValue((__ \ 'history), Json.toJson(v))

  def copy(project: Project): Project = new Project(this.json.as[JsObject] ++ project.json.as[JsObject])
  def copy(json: JsValue): Project = Project(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(project, _) => project
    case JsError(_) => new Project(this.json)
  }
}

/**
  * Factory class for creating [[Project]] instances.
  */
object Project {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError
  import utils.common.typeExtensions._

  private val MaxNameLength = configuration.getInt("core.project.nameLength").getOrElse(60)
  private val MaxBlurbLength = configuration.getInt("core.project.blurbLength").getOrElse(160)

  /**
    * Initializes a new instance of the [[Project]] class with the specified JSON.
    *
    * @param json     The project data as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[Project] = {
    { relaxed match {
      case Some(r) => validateProject(r)
      case _ => validateProject
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new Project(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Project]] class with the specified values.
    *
    * @param id           The identifier of the project.
    * @param pseudoid     The pseudo-identifier of the project.
    * @param accountId    The identifier of the account that owns the project.
    * @param ownerIdentityMode One of the [[models.auth.IdentityMode]] values.
    * @param name         The name of the project.
    * @param categories   The categories the project belongs to.
    * @param blurb        Short blurb of the project.
    * @param description  The description of the project.
    * @param location     The location of the project.
    * @param picked       A Boolean value indicating whether the project was picked.
    * @param state        One of the [[services.core.projects.ProjectFsm]] values.
    * @param fundingInfo  The funding information of the project.
    * @param rewards      The rewarding plan of the project.
    * @param faqs         The frequently asked questions about the project.
    * @param history      The history of the project.
    * @param creationTime The time the project was created.
    * @return             A new instance of the [[Project]] class.
    */
  def apply(
    id: Option[String] = None,
    pseudoid: Option[String] = None,
    accountId: Option[String] = None,
    ownerIdentityMode: Option[IdentityMode] = None,
    name: Option[String] = None,
    categories: Option[List[String]] = None,
    blurb: Option[String] = None,
    description: Option[String] = None,
    location: Option[String] = None,
    picked: Option[Boolean] = None,
    state: Option[State] = None,
    fundingInfo: Option[FundingInfo] = None,
    rewards: Option[List[Reward]] = None,
    faqs: Option[List[Faq]] = None,
    history: Option[List[HistoryEvent]] = None,
    creationTime: Option[DateTime] = None
  ): Project = new Project(
    projectWrites.writes(
      id,
      pseudoid,
      accountId,
      ownerIdentityMode,
      name,
      categories,
      blurb,
      description,
      location,
      picked,
      state,
      fundingInfo,
      rewards,
      faqs,
      history,
      creationTime
    )
  )

  /**
    * Extracts the content of the specified [[Project]].
    *
    * @param project  The [[Project]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `project` is `null`.
    */
  def unapply(project: Project) = {
    if (project eq null) None
    else Some((
      project.id,
      project.pseudoid,
      project.accountId,
      project.ownerIdentityMode,
      project.name,
      project.categories,
      project.blurb,
      project.description,
      project.location,
      project.picked,
      project.state,
      project.fundingInfo,
      project.rewards,
      project.faqs,
      project.history,
      project.creationTime
    ))
  }

  /**
    * Serializes/Deserializes a [[Project]] to/from JSON.
    */
  implicit val projectFormat: Format[Project] = projectFormat(None)
  def projectFormat(relaxed: Option[Boolean]): Format[Project] = new Format[Project] {
    def reads(json: JsValue) = Project(json, relaxed)
    def writes(project: Project) = project.json
  }

  /**
    * Serializes a [[Project]] to JSON.
    * @note Used internally by `apply`.
    */
  private val projectWrites = (
    (__ \ 'id).writeNullable[String] ~
    (__ \ 'pseudoid).writeNullable[String] ~
    (__ \ 'accountId).writeNullable[String] ~
    (__ \ 'ownerIdentityMode).writeNullable[IdentityMode] ~
    (__ \ 'name).writeNullable[String] ~
    (__ \ 'categories).writeNullable[List[String]] ~
    (__ \ 'blurb).writeNullable[String] ~
    (__ \ 'description).writeNullable[String] ~
    (__ \ 'location).writeNullable[String] ~
    (__ \ 'picked).writeNullable[Boolean] ~
    (__ \ 'state).writeNullable[State] ~
    (__ \ 'fundingInfo).writeNullable[FundingInfo] ~
    (__ \ 'rewards).writeNullable[List[Reward]] ~
    (__ \ 'faqs).writeNullable[List[Faq]] ~
    (__ \ 'history).writeNullable[List[HistoryEvent]] ~
    (__ \ 'creationTime).writeNullable[DateTime]
  ).tupled

  /**
    * Validates the JSON representation of the project state.
    */
  private val state = (__ \ 'state).json.pick[JsValue] andThen validateState

  /**
    * Validates the JSON representation of the funding information.
    */
  private val fundingInfo: Reads[JsObject] = fundingInfo(None)
  private def fundingInfo(relaxed: Option[Boolean]): Reads[JsObject] = {
    (__ \ 'fundingInfo).json.pick[JsValue] andThen { relaxed match {
      case Some(r) => validateFundingInfo(r)
      case _ => validateFundingInfo
    }}
  }

  /**
    * Validates the JSON representation of the reward list.
    */
  private def validateRewards(relaxed: Option[Boolean]) = {
    verifyingIf((arr: JsArray) => arr.value.nonEmpty) { relaxed match {
      case Some(r) => Reads.list(validateReward(r))
      case _ => Reads.list(validateReward)
    }}
  }
  private def rewards: Reads[JsArray] = rewards(None)
  private def rewards(relaxed: Option[Boolean]): Reads[JsArray] = {
    (__ \ 'rewards).json.pick[JsArray] andThen validateRewards(relaxed)
  }

  /**
    * Validates the JSON representation of the frequently asked question list.
    */
  private def validateFaqs(relaxed: Option[Boolean]) = {
    verifyingIf((arr: JsArray) => arr.value.nonEmpty)(Reads.list(validateFaq(relaxed.getOrElse(true))))
  }
  private def faqs: Reads[JsArray] = faqs(None)
  private def faqs(relaxed: Option[Boolean]): Reads[JsArray] = {
    (__ \ 'faqs).json.pick[JsArray] andThen validateFaqs(relaxed)
  }

  /**
    * Validates the JSON representation of the project history.
    */
  private val validateHistory = verifyingIf((arr: JsArray) =>
    arr.value.nonEmpty)(Reads.list(validateHistoryEvent))
  private val history: Reads[JsArray] = {
    (__ \ 'history).json.pick[JsArray] andThen validateHistory
  }

  /**
    * Validates the JSON representation of a [[Project]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Project]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateProject = (
    ((__ \ 'id).json.pickBranch orEmpty) ~
    ((__ \ 'pseudoid).json.pickBranch orEmpty) ~
    ((__ \ 'accountId).json.pickBranch orEmpty) ~
    ((__ \ 'ownerIdentityMode).json.pickBranch orEmpty) ~
    ((__ \ 'name).json.pickBranch orEmpty) ~
    ((__ \ 'categories).json.pickBranch orEmpty) ~
    ((__ \ 'blurb).json.pickBranch orEmpty) ~
    ((__ \ 'description).json.pickBranch orEmpty) ~
    ((__ \ 'location).json.pickBranch orEmpty) ~
    ((__ \ 'picked).json.pickBranch(Reads.of[JsBoolean]) orEmpty) ~
    ((__ \ 'state).json.copyFrom(state) orEmpty) ~
    ((__ \ 'fundingInfo).json.copyFrom(fundingInfo) orEmpty) ~
    ((__ \ 'rewards).json.copyFrom(rewards) orEmpty) ~
    ((__ \ 'faqs).json.copyFrom(faqs) orEmpty) ~
    ((__ \ 'history).json.copyFrom(history) orEmpty) ~
    ((__ \ 'creationTime).json.pickBranch orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[Project]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[Project]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateProject(relaxed: Boolean) = (
    ((__ \ 'ownerIdentityMode).json.pickBranch(Reads.of[JsString] <~ identityMode) orEmptyIf relaxed) ~
    ((__ \ 'name).json.pickBranch(Reads.of[JsString] <~ Reads.maxLength[String](MaxNameLength)) orEmptyIf relaxed) ~
    ((__ \ 'categories).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'blurb).json.pickBranch(Reads.of[JsString] <~ Reads.maxLength[String](MaxBlurbLength)) orEmptyIf relaxed) ~
    ((__ \ 'description).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'location).json.pickBranch orEmptyIf relaxed) ~
    ((__ \ 'fundingInfo).json.copyFrom(fundingInfo(Some(relaxed))) orEmptyIf relaxed) ~
    ((__ \ 'rewards).json.copyFrom(rewards(Some(relaxed))) orEmpty) ~
    ((__ \ 'faqs).json.copyFrom(faqs(Some(relaxed))) orEmpty)
  ).reduce
}
