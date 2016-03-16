/*#
  * @file Coin.scala
  * @begin 5-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents a monetary value, optionally associated with a reference currency.
  *
  * @constructor  Initializes a new instance of the [[Coin]] class.
  * @param json   The monetary value as JSON.
  */
class Coin protected(protected var json: JsValue) extends JsModel with api.Coin {

  def value = json as (__ \ 'value).read[Double]
  def value_= (v: Double) = setValue((__ \ 'value), Json.toJson(v))
  def currency = json as (__ \ 'currency).read[String]
  def currency_= (v: String) = setValue((__ \ 'currency), Json.toJson(v))
  def refCurrency = json as (__ \ 'refCurrency).readNullable[String]
  def refCurrency_= (v: Option[String]) = setValue((__ \ 'refCurrency), Json.toJson(v))
  def rate = json as (__ \ 'rate).readNullable[Double]
  def rate_= (v: Option[Double]) = setValue((__ \ 'rate), Json.toJson(v))

  def copy(coin: Coin): Coin = new Coin(this.json.as[JsObject] ++ coin.json.as[JsObject])
  def copy(json: JsValue): Coin = Coin(this.json.as[JsObject] ++ json.as[JsObject]) match {
    case JsSuccess(coin, _) => coin
    case JsError(_) => new Coin(this.json)
  }
}

/**
  * Factory class for creating [[Coin]] instances.
  */
object Coin extends Validator {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._
  import play.api.data.validation.ValidationError
  import utils.common.typeExtensions._

  /**
    * Initializes a new instance of the [[Coin]] class with the specified JSON.
    *
    * @param json The monetary value as JSON.
    * @return     A `JsResult` value that contains the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Coin] = {
    validateCoin.reads(json).fold(
      valid = { validated => JsSuccess(new Coin(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Initializes a new instance of the [[Coin]] class with the specified values.
    *
    * @param value        The number of monetary units.
    * @param currency     The currency of `value`.
    * @param refCurrency  The reference currency.
    * @param rate         The exchange rate from `currency` to `refCurrency`.
    * @return             A new instance of the [[Coin]] class.
    */
  def apply(
    value: Double,
    currency: String,
    refCurrency: Option[String] = None,
    rate: Option[Double] = None
  ): Coin = new Coin(
    coinWrites.writes(
      value,
      currency.toUpperCase,
      refCurrency.map(_.toUpperCase),
      rate
    )
  ) 

  /**
    * Extracts the content of the specified [[Coin]].
    *
    * @param coin The [[Coin]] to extract the content from.
    * @return     An `Option` that contains the extracted data,
    *             or `None` if `coin` is `null`.
    */
  def unapply(coin: Coin) = {
    if (coin eq null) None
    else Some((
      coin.value,
      coin.currency,
      coin.refCurrency,
      coin.rate
    ))
  }

  /**
    * Serializes/Deserializes a [[Coin]] to/from JSON.
    */
  implicit val coinFormat = new Format[Coin] {
    def reads(json: JsValue) = Coin(json)
    def writes(coin: Coin) = coin.json
  }

  /**
    * Serializes a [[Coin]] to JSON.
    * @note Used internally by `apply`.
    */
  private val coinWrites = (
    (__ \ 'value).write[Double] ~
    (__ \ 'currency).write[String] ~
    (__ \ 'refCurrency).writeNullable[String] ~
    (__ \ 'rate).writeNullable[Double]
  ).tupled
  
  /**
    * Validates the JSON representation of a [[Coin]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Coin]].
    * @note   This validator is intended for JSON coming from both inside and
    *         outside the application.
    */
  val validateCoin = (
    ((__ \ 'value).json.pickBranch(Reads.of[JsNumber])) ~
    ((__ \ 'currency).json.pickBranch(Reads.of[JsString] <~ currency)) ~
    ((__ \ 'refCurrency).json.pickBranch(Reads.of[JsString] <~ currency) orEmpty) ~
    ((__ \ 'rate).json.pickBranch(Reads.of[JsNumber]) orEmpty) ~ (
      ((__ \ 'currency).json.update(toUpperCase)) andThen
      ((__ \ 'refCurrency).json.update(toUpperCase) orEmpty)
  )).reduce

  /**
    * Validates the currency associated with a monetary value.
    */
  def currency(implicit reads: Reads[String]) = {
    import services.pay.PayGateway.SupportedCurrencies

    Reads[String](js =>
      reads.reads(js).flatMap { v =>
        if (SupportedCurrencies.contains(v)) JsSuccess(v)
        else JsError(ValidationError("error.currency", SupportedCurrencies.mkString("|")))
      }
    )
  }
}
