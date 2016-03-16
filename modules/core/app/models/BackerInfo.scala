/*#
  * @file BackerInfo.scala
  * @begin 26-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.core

import play.api.libs.json._
import utils.common.Validator
import utils.common.Formats._
import models.common.{Address, JsModel}
import models.common.Address._
import models.auth.IdentityMode._

/**
  * Provides information about a backer that pledged to a project.
  *
  * @constructor  Initializes a new instance of the [[BackerInfo]] class.
  * @param json   The backer information as JSON.
  */
class BackerInfo protected(protected var json: JsValue) extends JsModel with api.BackerInfo {

  def accountId = json as (__ \ 'accountId).read[String]
  def accountId_= (v: String) = setValue((__ \ 'accountId), Json.toJson(v))
  def identityMode = json as (__ \ 'identityMode).read[IdentityMode]
  def identityMode_= (v: IdentityMode) = setValue((__ \ 'identityMode), Json.toJson(v))
  def fundingCoinAddresses = json as (__ \ 'fundingCoinAddresses).readNullable[List[String]]
  def fundingCoinAddresses_= (v: Option[List[String]]) = setValue((__ \ 'fundingCoinAddresses), Json.toJson(v))
  def refundCoinAddress = json as (__ \ 'refundCoinAddress).readNullable[String]
  def refundCoinAddress_= (v: Option[String]) = setValue((__ \ 'refundCoinAddress), Json.toJson(v))
  def shippingAddress = json as (__ \ 'shippingAddress).readNullable[Address]
  def shippingAddress_= (v: Option[Address]) = setValue((__ \ 'shippingAddress), Json.toJson(v))
  def notice = json as (__ \ 'notice).readNullable[String]
  def notice_= (v: Option[String]) = setValue((__ \ 'notice), Json.toJson(v))

  def copy(backerInfo: BackerInfo): BackerInfo = new BackerInfo(this.json.as[JsObject] ++ backerInfo.json.as[JsObject])
  def copy(json: JsValue): BackerInfo = BackerInfo(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(backerInfo, _) => backerInfo
    case JsError(_) => new BackerInfo(this.json)
  }
}

/**
  * Factory class for creating [[BackerInfo]] instances.
  */
object BackerInfo extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[BackerInfo]] class with the specified JSON.
    *
    * @param json The pledge origin as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[BackerInfo] = {
    validateBackerInfo.reads(json).fold(
      valid = { validated => JsSuccess(new BackerInfo(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[BackerInfo]] class with the specified values.
    *
    * @param accountId          The identifier of the backer account.
    * @param identityMode       One of the [[models.auth.IdentityMode]] values.
    * @param fundingCoinAddresses The coin addresses funds came from.
    * @param refundCoinAddress  The coin address where to send funds back in case of failure.
    * @param shippingAddress    The address where the delivery of physical rewards will be received.
    * @param notice             Backer's notice regarding a reward or refund, if any.
    * @return                   A new instance of the [[BackerInfo]] class.
    */
  def apply(
    accountId: String,
    identityMode: IdentityMode,
    fundingCoinAddresses: Option[List[String]] = None,
    refundCoinAddress: Option[String] = None,
    shippingAddress: Option[Address] = None,
    notice: Option[String] = None
  ): BackerInfo = new BackerInfo(
    backerInfoWrites.writes(
      accountId,
      identityMode,
      fundingCoinAddresses,
      refundCoinAddress,
      shippingAddress,
      notice
    )
  ) 

  /**
    * Extracts the content of the specified [[BackerInfo]].
    *
    * @param backerInfo The [[BackerInfo]] to extract the content from.
    * @return             An `Option` that contains the extracted data,
    *                     or `None` if `backerInfo` is `null`.
    */
  def unapply(backerInfo: BackerInfo) = {
    if (backerInfo eq null) None
    else Some((
      backerInfo.accountId,
      backerInfo.identityMode,
      backerInfo.fundingCoinAddresses,
      backerInfo.refundCoinAddress,
      backerInfo.shippingAddress,
      backerInfo.notice
    ))
  }

  /**
    * Serializes/Deserializes a [[BackerInfo]] to/from JSON.
    */
  implicit val backerInfoFormat = new Format[BackerInfo] {
    def reads(json: JsValue) = BackerInfo(json)
    def writes(backerInfo: BackerInfo) = backerInfo.json
  }

  /**
    * Serializes a [[BackerInfo]] to JSON.
    * @note Used internally by `apply`.
    */
  private val backerInfoWrites = (
    (__ \ 'accountId).write[String] ~
    (__ \ 'identityMode).write[IdentityMode] ~
    (__ \ 'fundingCoinAddresses).writeNullable[List[String]] ~
    (__ \ 'refundCoinAddress).writeNullable[String] ~
    (__ \ 'shippingAddress).writeNullable[Address] ~
    (__ \ 'notice).writeNullable[String]
  ).tupled

  /**
    * Validates the JSON representation of the funding coin address list.
    */
  private val validateFundingCoinAddresses = verifyingIf((arr: JsArray) =>
    arr.value.nonEmpty)(Reads.list(coinAddress))
  private val fundingCoinAddresses: Reads[JsArray] = {
    (__ \ 'fundingCoinAddresses).json.pick[JsArray] andThen validateFundingCoinAddresses
  }

  /**
    * Validates the JSON representation of the shipping address.
    */
  private val shippingAddress = (__ \ 'shippingAddress).json.pick[JsValue] andThen validateAddress(false)

  /**
    * Validates the JSON representation of a [[BackerInfo]].
    *
    * @return A `Reads` that validates the JSON representation of a [[BackerInfo]].
    * @note   This validator is intended for JSON coming from inside the application.
    */
  def validateBackerInfo = (
    ((__ \ 'accountId).json.pickBranch) ~
    ((__ \ 'identityMode).json.pickBranch) ~
    ((__ \ 'fundingCoinAddresses).json.copyFrom(fundingCoinAddresses) orEmpty) ~
    ((__ \ 'refundCoinAddress).json.pickBranch orEmpty) ~
    ((__ \ 'shippingAddress).json.copyFrom(shippingAddress) orEmpty) ~
    ((__ \ 'notice).json.pickBranch orEmpty)
  ).reduce
}
