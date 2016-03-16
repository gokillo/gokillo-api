/*#
  * @file WalletInfo.scala
  * @begin 9-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import org.joda.time.DateTime
import play.api.libs.json._
import models.common.JsModel
import CoinNet._
import WalletTransaction._

/**
  * Provides information about a wallet.
  *
  * @constructor  Initializes a new instance of the [[WalletInfo]] class.
  * @param json   The wallet info as JSON.
  */
class WalletInfo protected(protected var json: JsValue) extends JsModel with api.WalletInfo {

  def filename = json as (__ \ 'filename).read[String]
  def description = json as (__ \ 'description).readNullable[String]
  def receiveAddress = json as (__ \ 'receiveAddress).read[String]
  def balance = json as (__ \ 'balance).read[Double]
  def spendable = json as (__ \ 'spendable).read[Double]
  def net = json as (__ \ 'net).read[CoinNet]
  def ver = json as (__ \ 'version).read[Int]
  def encrypted = json as (__ \ 'encrypted).read[Boolean]
  def pendingTransactions = json as (__ \ 'pendingTransactions).readNullable[List[WalletTransaction]]

  def copy(json: JsValue) = throw new UnsupportedOperationException
}

/**
  * Factory class for creating [[WalletInfo]] instances.
  */
object WalletInfo {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  /**
    * Initializes a new instance of the [[WalletInfo]] class with the specified values.
    *
    * @param filename             The name of the wallet on the file system.
    * @param description          The description of the wallet.
    * @param receiveAddress       The current receive address of the wallet.
    * @param balance              The balance calculated assuming all pending transactions.
    * @param spendable            The balance that could be safely used to create new spends.
    * @param net                  One of the [[CoinNet]] values.
    * @param version              The version of the wallet.
    * @param encrypted            A Boolean value indicating whether or not the wallet is encrypted.
    * @param pendingTransactions  The pending transactions associated with the wallet.
    * @return                     A new instance of the [[WalletInfo]] class.
    */
  def apply(
    filename: String,
    description: Option[String],
    receiveAddress: String,
    balance: Double,
    spendable: Double,
    net: CoinNet,
    version: Int,
    encrypted: Boolean,
    pendingTransactions: Option[List[WalletTransaction]] = None
  ): WalletInfo = new WalletInfo(
    walletInfoWrites.writes(
      filename,
      description,
      receiveAddress,
      balance,
      spendable,
      net,
      version,
      encrypted,
      pendingTransactions
    )
  ) 

  /**
    * Extracts the content of the specified [[WalletInfo]].
    *
    * @param walletInfo  The [[WalletInfo]] to extract the content from.
    * @return         An `Option` that contains the extracted data,
    *                 or `None` if `walletInfo` is `null`.
    */
  def unapply(walletInfo: WalletInfo) = {
    if (walletInfo eq null) None
    else Some((
      walletInfo.filename,
      walletInfo.description,
      walletInfo.receiveAddress,
      walletInfo.balance,
      walletInfo.spendable,
      walletInfo.net,
      walletInfo.version,
      walletInfo.encrypted,
      walletInfo.pendingTransactions
    ))
  }

  /**
    * Serializes a [[WalletInfo]] to JSON.
    * @note Used internally by `apply`.
    */
  private val walletInfoWrites = (
    (__ \ 'filename).write[String] ~
    (__ \ 'description).writeNullable[String] ~
    (__ \ 'receiveAddress).write[String] ~
    (__ \ 'balance).write[Double] ~
    (__ \ 'spendable).write[Double] ~
    (__ \ 'net).write[CoinNet] ~
    (__ \ 'version).write[Int] ~
    (__ \ 'encrypted).write[Boolean] ~
    (__ \ 'pendingTransactions).writeNullable[List[WalletTransaction]]
  ).tupled
}
