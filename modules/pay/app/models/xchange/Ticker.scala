/*#
  * @file Ticker.scala
  * @begin 17-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay.xchange

import play.api.libs.json._
import utils.common.Validator
import models.common.JsModel

/**
  * Represents `ticker` data coming from the exchange service.
  *
  * @constructor  Initializes a new instance of the [[Ticker]] class.
  * @param json   The `ticker` data as JSON.
  */
class Ticker protected(protected var json: JsValue) extends JsModel {

  def ask = json as (__ \ 'ask).read[Double]
  def bid = json as (__ \ 'bid).read[Double]
  def high = json as (__ \ 'high).read[Double]
  def last = json as (__ \ 'last).read[Double]
  def low = json as (__ \ 'low).read[Double]
  def volume = json as (__ \ 'volume).read[Double]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[Ticker]] instances.
  */
object Ticker extends Validator {

  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[Ticker]] class with the specified JSON.
    *
    * @param json The `ticker` data as JSON.
    * @return     A `JsResult` value containing the new class instance,
    *             or a `JsError` if `json` is not valid.
    */
  def apply(json: JsValue): JsResult[Ticker] = {
    validateTicker.reads(json).fold(
      valid = { validated => JsSuccess(new Ticker(validated)) },
      invalid = { errors => JsError(errors) }
    )
  }

  /**
    * Serializes/Deserializes a [[Ticker]] to/from JSON.
    */
  implicit val tickerFormat = new Format[Ticker] {
    def reads(json: JsValue) = Ticker(json)
    def writes(ticker: Ticker) = ticker.json
  }

  /**
    * Validates the JSON representation of a [[Ticker]].
    *
    * @return A `Reads` that validates the JSON representation of a [[Ticker]].
    */
  def validateTicker = (
    ((__ \ 'ask).json.update(toNumber)) andThen
    ((__ \ 'bid).json.update(toNumber)) andThen
    ((__ \ 'high).json.update(toNumber)) andThen
    ((__ \ 'last).json.update(toNumber)) andThen
    ((__ \ 'low).json.update(toNumber)) andThen
    ((__ \ 'volume).json.update(toNumber))
  )
}
