/*#
  * @file TransactionType.scala
  * @begin 10-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.pay

import play.api.libs.json._
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines transaction types.
  */
object TransactionType extends Enumeration {

  type TransactionType = Value

  /**
    * Defines a transaction where coins are deposited into a wallet.
    */
  val Deposit = Value("deposit")

  /**
    * Defines a transaction where coins are withdrawn from a wallet.
    */
  val Withdrawal = Value("withdrawal")

  /**
    * Serializes/Deserializes a [[TransactionType]] to/from JSON.
    */
  implicit val transactionTypeFormat = new Format[TransactionType] {
    def reads(json: JsValue) = JsSuccess(TransactionType(json.as[JsString].value))
    def writes(transactionType: TransactionType) = JsString(transactionType.toString)
  }

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): TransactionType = {
    try {
      return TransactionType.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("transaction type", name)
    }
  }
}
