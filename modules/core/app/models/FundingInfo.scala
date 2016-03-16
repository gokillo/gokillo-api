/*#
  * @file FundingInfo.scala
  * @begin 15-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import org.joda.time.DateTime
import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.JsModel

/**
  * Provides information about the funding of a project.
  *
  * @constructor  Initializes a new instance of the [[FundingInfo]] class.
  * @param json   The funding information as JSON.
  */
class FundingInfo protected(protected var json: JsValue) extends JsModel with api.FundingInfo {

  // update end time according to current start time and duration
  updateEndTime

  def coinAddress = json as (__ \ 'coinAddress).readNullable[String]
  def coinAddress_= (v: Option[String]) = setValue((__ \ 'coinAddress), Json.toJson(v))
  def targetAmount = json as (__ \ 'targetAmount).readNullable[Double]
  def targetAmount_= (v: Option[Double]) = setValue((__ \ 'targetAmount), Json.toJson(v))
  def raisedAmount = json as (__ \ 'raisedAmount).readNullable[Double]
  def raisedAmount_= (v: Option[Double]) = setValue((__ \ 'raisedAmount), Json.toJson(v))
  def currency = json as (__ \ 'currency).readNullable[String]
  def currency_= (v: Option[String]) = setValue((__ \ 'currency), Json.toJson(v))
  def pledgeCount = json as (__ \ 'pledgeCount).readNullable[Int]
  def pledgeCount_= (v: Option[Int]) = setValue((__ \ 'pledgeCount), Json.toJson(v))
  def duration = json as (__ \ 'duration).readNullable[Int]
  def duration_= (v: Option[Int]) = { setValue((__ \ 'duration), Json.toJson(v)); updateEndTime }
  def startTime = json as (__ \ 'startTime).readNullable[DateTime]
  def startTime_= (v: Option[DateTime]) = { setValue((__ \ 'startTime), Json.toJson(v)); updateEndTime }
  def endTime = json as (__ \ 'endTime).readNullable[DateTime]

  private def updateEndTime = {
    var dateTime: Option[DateTime] = None
    duration.map(d => startTime.map(st => dateTime = Some(st.plusDays(d))))
    setValue((__ \ 'endTime), Json.toJson(dateTime))
  }

  def copy(fundingInfo: FundingInfo): FundingInfo = new FundingInfo(this.json.as[JsObject] ++ fundingInfo.json.as[JsObject])
  def copy(json: JsValue): FundingInfo = FundingInfo(this.json.as[JsObject] ++ json.as[JsObject], None) match {
    case JsSuccess(fundingInfo, _) => fundingInfo
    case JsError(_) => new FundingInfo(this.json)
  }
}

/**
  * Factory class for creating [[FundingInfo]] instances.
  */
object FundingInfo extends Validator {

  import play.api.Play.current
  import play.api.Play.configuration
  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  private final val MinFundraisingDuration = configuration.getInt("core.project.minFundraisingDuration").getOrElse(1)
  private final val MaxFundraisingDuration = configuration.getInt("core.project.maxFundraisingDuration").getOrElse(90)

  /**
    * Initializes a new instance of the [[FundingInfo]] class with the specified JSON.
    *
    * @param json     The funding information as JSON.
    * @param relaxed  An `Option` value containing a Boolean indicating whether or
    *                 not `json` just needs to be valid even if not complete.
    * @return         A `JsResult` value containing the new class instance,
    *                 or a `JsError` if `json` is not valid.
    * @note           When `json` comes from outside the application, `relaxed` should
    *                 be `Some` to ensure only publicly modifiable fields are set.
    */
  def apply(json: JsValue, relaxed: Option[Boolean]): JsResult[FundingInfo] = {
    { relaxed match {
      case Some(r) => validateFundingInfo(r)
      case _ => validateFundingInfo
    }}.reads(json).fold(
      valid = { validated => JsSuccess(new FundingInfo(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[FundingInfo]] class with the specified values.
    *
    * @param coinAddress  The coin address where to send funds in case of success.
    * @param targetAmount The target amount.
    * @param raisedAmount The amount raised so far.
    * @param currency     The amount currency.
    * @param pledgeCount  The number of pledges.
    * @param duration     The number of days the fundraising campaign lasts.
    * @param startTime    The start time of the fundraising campaign.
    * @param endTime      The end time of the fundraising campaign.
    * @return             A new instance of the [[FundingInfo]] class.
    */
  def apply(
    coinAddress: Option[String] = None,
    targetAmount: Option[Double] = None,
    raisedAmount: Option[Double] = None,
    currency: Option[String] = None,
    pledgeCount: Option[Int] = None,
    duration: Option[Int],
    startTime: Option[DateTime] = None,
    endTime: Option[DateTime] = None
  ): FundingInfo = new FundingInfo(
    fundingInfoWrites.writes(
      coinAddress,
      targetAmount,
      raisedAmount,
      currency.map(_.toUpperCase),
      pledgeCount,
      duration,
      startTime,
      endTime
    )
  ) 

  /**
    * Extracts the content of the specified [[FundingInfo]].
    *
    * @param fundingInfo  The [[FundingInfo]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `fundingInfo` is `null`.
    */
  def unapply(fundingInfo: FundingInfo) = {
    if (fundingInfo eq null) None
    else Some((
      fundingInfo.coinAddress,
      fundingInfo.targetAmount,
      fundingInfo.raisedAmount,
      fundingInfo.currency,
      fundingInfo.pledgeCount,
      fundingInfo.duration,
      fundingInfo.startTime,
      fundingInfo.endTime
    ))
  }

  /**
    * Serializes/Deserializes a [[FundingInfo]] to/from JSON.
    */
  implicit val fundingInfoFormat: Format[FundingInfo] = fundingInfoFormat(None)
  def fundingInfoFormat(relaxed: Option[Boolean]) = new Format[FundingInfo] {
    def reads(json: JsValue) = FundingInfo(json, relaxed)
    def writes(fundingInfo: FundingInfo) = fundingInfo.json
  }

  /**
    * Serializes a [[FundingInfo]] to JSON.
    * @note Used internally by `apply`.
    */
  private val fundingInfoWrites = (
    (__ \ 'coinAddress).writeNullable[String] ~
    (__ \ 'targetAmount).writeNullable[Double] ~
    (__ \ 'raisedAmount).writeNullable[Double] ~
    (__ \ 'currency).writeNullable[String] ~
    (__ \ 'pledgeCount).writeNullable[Int] ~
    (__ \ 'duration).writeNullable[Int] ~
    (__ \ 'startTime).writeNullable[DateTime] ~
    (__ \ 'endTime).writeNullable[DateTime]
  ).tupled
  
  /**
    * Validates the JSON representation of a [[FundingInfo]].
    *
    * @return A `Reads` that validates the JSON representation of a [[FundingInfo]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateFundingInfo = (
    ((__ \ 'coinAddress).json.pickBranch orEmpty) ~
    ((__ \ 'targetAmount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'raisedAmount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'currency).json.pickBranch orEmpty) ~
    ((__ \ 'pledgeCount).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'duration).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~
    ((__ \ 'startTime).json.pickBranch(Reads.of[JsString] <~ isoDateTime) orEmpty) ~
    ((__ \ 'endTime).json.pickBranch(Reads.of[JsString] <~ isoDateTime) orEmpty) ~
    ((__ \ 'currency).json.update(toUpperCase) orEmpty)
  ).reduce

  /**
    * Validates the JSON representation of a [[FundingInfo]].
    *
    * @param relaxed  A Boolean value indicating whether or not validation is relaxed,
    *                 i.e. no field is mandatory.
    * @return         A `Reads` that validates the JSON representation of a [[FundingInfo]].
    * @note           This validator is intended for JSON coming from outside the application.
    */
  def validateFundingInfo(relaxed: Boolean) = (
    ((__ \ 'coinAddress).json.pickBranch(Reads.of[JsString] <~ coinAddress) orEmptyIf relaxed) ~
    ((__ \ 'targetAmount).json.pickBranch(Reads.of[JsNumber] <~ greaterThanZero) orEmptyIf relaxed) ~
    ((__ \ 'currency).json.pickBranch(Reads.of[JsString] <~ models.pay.Coin.currency) orEmptyIf relaxed) ~
    ((__ \ 'duration).json.pickBranch(Reads.of[JsNumber] <~ equalOrGreaterThan(
      MinFundraisingDuration
    ) andKeep Reads.of[JsNumber] <~ equalOrLessThan(
      MaxFundraisingDuration
    )) orEmptyIf relaxed) ~
    ((__ \ 'currency).json.update(toUpperCase) orEmptyIf relaxed)
  ).reduce
}
