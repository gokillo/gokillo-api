/*#
  * @file WalletTransaction.scala
  * @begin 13-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import models.common.JsModel

/**
  * Provides information about a wallet transaction.
  *
  * @constructor  Initializes a new instance of the [[WalletTransaction]] class.
  * @param json   The wallet transaction as JSON.
  */
class WalletTransaction protected(protected var json: JsValue) extends JsModel with api.WalletTransaction {

  def sentFrom = json as (__ \ 'sentFrom).read[Double]
  def sentTo = json as (__ \ 'sentFrom).read[Double]
  def total = json as (__ \ 'sentFrom).read[Double]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[WalletTransaction]] instances.
  */
object WalletTransaction {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[WalletTransaction]] class with the
    * specified values.
    *
    * @param sentFrom The sum of inputs that are spending coins in the wallet.
    * @param sentTo   The sum of outputs that are sending coins to the wallet.
    * @return         A new instance of the [[WalletTransaction]] class.
    */
  def apply(
    sentFrom: Double,
    sentTo: Double
  ): WalletTransaction = new WalletTransaction(
    walletTransactionWrites.writes(
      sentFrom,
      sentTo,
      sentFrom - sentTo
    )
  ) 

  /**
    * Extracts the content of the specified [[WalletTransaction]].
    *
    * @param walletTransaction  The [[WalletTransaction]] to extract the content from.
    * @return       An `Option` that contains the extracted data,
    *               or `None` if `walletTransaction` is `null`.
    */
  def unapply(walletTransaction: WalletTransaction) = {
    if (walletTransaction eq null) None
    else Some((
      walletTransaction.sentFrom,
      walletTransaction.sentTo,
      walletTransaction.total
    ))
  }

  /**
    * Serializes/Deserializes a [[WalletTransaction]] to/from JSON.
    */
  implicit val walletTransactionFormat = new Format[WalletTransaction] {
    def reads(json: JsValue) = JsSuccess(new WalletTransaction(json))
    def writes(walletTransaction: WalletTransaction) = walletTransaction.json
  }

  /**
    * Serializes a [[WalletTransaction]] to JSON.
    * @note Used internally by `apply`.
    */
  private val walletTransactionWrites = (
    (__ \ 'sentFrom).write[Double] ~
    (__ \ 'sentTo).write[Double] ~
    (__ \ 'total).write[Double]
  ).tupled
}
