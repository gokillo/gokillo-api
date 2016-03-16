/*#
  * @file WalletAppKit.scala
  * @begin 28-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import java.io.File
import play.api.Play.current
import play.api.Play.configuration
import org.bitcoinj.core._
import org.bitcoinj.core.Wallet.{BalanceType, SendRequest}
import org.bitcoinj.kits.{WalletAppKit => BaseWalletAppKit}
import models.pay.CoinNet._

/**
  * Utility class that wraps the boilerplate needed to set up a bitcoinj app.
  *
  * @constructor      Initializes a new instance of the [[WalletAppKit]] class.
  * @param context    The context that wraps the network parameters needed to work with the coin chain.
  * @param directory  The directory where bitcoinj stores wallet files.
  * @param filePrefix The prefix used to name wallet files.
  */
class WalletAppKit private(
  context: Context,
  directory: File,
  filePrefix: String
) extends BaseWalletAppKit(context, directory, filePrefix) {

  import WalletAppKit._

  private val Secret = configuration.getString("application.secret")

  // initialize user agent
  configuration.getString("application.name").foreach { userAgent =>
    configuration.getString("api.version").foreach { version =>
      setUserAgent(userAgent, version)
    }
  }

  /** Invoked by the `WalletAppKit` to create a wallet. */
  override protected def createWallet = {
    val wallet = super.createWallet
    Secret.foreach { secret => wallet.encrypt(secret) }
    wallet
  }

  /**
    * Sends the specified amount to the specified coin address.
    *
    * @param coinAddress  The recipient coin address.
    * @param amount       The amount to send in cryptocurrency.
    */
  def sendCoins(coinAddress: String, amount: Double) = {
    Context.propagate(wallet.getContext)
    Secret match {
      case Some(secret) if wallet.isEncrypted =>
        val req = Wallet.SendRequest.to(new Address(params, coinAddress), Coin.valueOf(toNanoCoins(amount)))
        req.aesKey = wallet.getKeyCrypter.deriveKey(secret)
        wallet.sendCoins(req)
      case _ =>
        wallet.sendCoins(peerGroup, new Address(params, coinAddress), Coin.valueOf(toNanoCoins(amount)))
    }
  }
}

/**
  * Factory class for creating [[WalletAppKit]] instances.
  */
object WalletAppKit {

  import play.api.Play.current
  import play.api.Play.application
  import org.bitcoinj.params.{MainNetParams, TestNet3Params}
  import org.bitcoinj.uri.BitcoinURI

  private def mainNetParams = MainNetParams.get
  private def testNetParams = TestNet3Params.get

  // set default transaction fee app-wide
  SendRequest.DEFAULT_FEE_PER_KB = Coin.valueOf(10000)

  /** Gets a `NetworkParameters` from the specified `CoinNet` value. */
  val toNetParams = Map(Prod -> mainNetParams, Test -> testNetParams)

  /** Gets the default transaction fee. */
  def transactionFee: Double = fromNanoCoins(SendRequest.DEFAULT_FEE_PER_KB.value)

  /** Gets a `CoinNet` value from the specified `NetworkParameters`. */
  def fromNetParams(netParams: NetworkParameters): CoinNet = {
    netParams match {
      case netParamas: MainNetParams => Prod
      case _ => Test
    }
  }

  /**
    * Initializes a new instance of the [[WalletAppKit]] class with
    * the specified coin network.
    *
    * @param coinNet  One of the `CoinNet` values.
    * @return         A new instance of the [[WalletAppKit]] class.
    */
  def apply(coinNet: CoinNet) = new WalletAppKit(
    new Context(toNetParams(coinNet)),
    new File(application.path.getPath + File.separator + "wallets"),
    PayGateway.XchangeName
  )

  /**
    * Generates a Bitcoin URI according to scheme BIP 0021.
    *
    * @param coinAddress  The recipient coin address.
    * @param amount       The amount in cryptocoin units.
    * @param label        An optional label for `coinAddress`.
    * @param message      An optional message that describes the transaction.
    * @return             A string containing the Bitcoin URI.
    */
  def bitcoinUri(coinAddress: String, amount: Double, label: String, message: String): String = {
    BitcoinURI.convertToBitcoinURI(
      coinAddress,
      Coin.valueOf(toNanoCoins(amount)),
      label, message
    )
  }

  /**
    * Converts the specified amount to satoshi.
    * 
    * @param amount The amount to convert.
    * @return       The amount converted to satoshi.
    */
  def toNanoCoins(amount: Double): Long = (amount * math.pow(10, Coin.SMALLEST_UNIT_EXPONENT)).toLong

  /**
    * Converts the specified amount from satoshi.
    * 
    * @param amount The amount in satoshi.
    * @return       The amount converted from satoshi.
    */
  def fromNanoCoins(amount: Long): Double = Coin.valueOf(amount).toPlainString.toDouble
}
