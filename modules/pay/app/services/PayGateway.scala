/*#
  * @file PayGateway.scala
  * @begin 9-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.pay

import scala.concurrent._
import scala.util.{Try, Failure, Success}
import org.joda.time.{DateTime, DateTimeZone}
import brix.crypto.Secret
import play.api.Play.current
import play.api.Play.configuration
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.common._
import services.common.CommonErrors._
import services.pay.PayErrors._
import utils.common.{QrCode, WithRetry}
import utils.common.QrCode._
import utils.common.Formats._
import utils.common.env._
import utils.common.typeExtensions._
import models.common.{Status, RefId}
import models.common.Id._
import models.common.Status._
import models.pay._
import models.pay.OrderStatus._
import models.pay.OrderType._
import models.pay.RateType._
import models.pay.TransactionType._
import models.pay.xchange.{Transaction => _, _}
import models.pay.xchange.{Transaction => XTransaction}
import mongo._

/**
  * Provides functionality for receiving and sending money
  */
object PayGateway {

  @inline private val DefaultPerPage = 500

  private final val QrCodeSize = 250
  private val RatePlay = configuration.getDouble("pay.ratePlay").getOrElse(5.0)

  // message broker destination names
  final val XchangeDeposit = "xchangeDeposit"
  final val XchangeDepositOnTrade = "xchangeDepositOnTrade"
  final val XchangeWithdrawalOnTrade = "xchangeWithdrawalOnTrade"
  final val LocalDeposit = "localDeposit"

  final val USD = 0; final val EUR = 1; final val CCY = 2
  final val InvoiceValidity = configuration.getInt("pay.invoiceValidity").getOrElse(15)
  final val MinAmount = configuration.getDouble("pay.minAmount").getOrElse(10.0)

  private val orderService: OrderDaoServiceComponent#OrderDaoService = new OrderDaoServiceComponent
    with MongoOrderDaoComponent {
  }.daoService.asInstanceOf[OrderDaoServiceComponent#OrderDaoService]

  private val transactionService: TransactionDaoServiceComponent#TransactionDaoService = new TransactionDaoServiceComponent
    with MongoTransactionDaoComponent {
  }.daoService.asInstanceOf[TransactionDaoServiceComponent#TransactionDaoService]

  /** Gets the cryptocurrency used to send and receive money. */
  val Cryptocurrency = configuration.getString("pay.cryptocurrency").getOrElse("BTC")

  /** Gets the supported currencies. */
  val SupportedCurrencies = List("USD", "EUR", Cryptocurrency)

  /** Gets the name of the exchange service */
  val XchangeName = peers("xchange").name

  /** Gets the transaction fee per KB. */
  val transactionFee = WalletAppKit.transactionFee

  /** Gets the message broker associated with this service. */
  private var _messageBroker: MessageBroker = _
  def messageBroker = _messageBroker

  /** Gets the underlying wallet kit. */
  private var _walletAppKit: WalletAppKit = _
  def walletAppKit = _walletAppKit

  /**
    * Starts the pay gateway.
    * @note Called by `PayPlugin` when the application starts.
    */
  def start = {
    _messageBroker = MessageBroker("pay")
    _walletAppKit = WalletAppKit(CoinNet(configuration.getString("pay.coinNet") getOrElse "test"))
    _walletAppKit.startAsync
    _walletAppKit.awaitRunning
    _walletAppKit.wallet.addEventListener(new WalletListener)
  }

  /**
    * Stops the pay gateway.
    * @note Called by `PayPlugin` when the application stops.
    */
  def stop = {
    _messageBroker.shutdown
    _walletAppKit.stopAsync
    _walletAppKit.awaitTerminated
    _walletAppKit = null
  }

  /**
    * Gets the current exchange rates against the specified reference currency.
    *
    * @param refCurrency  The reference currency.
    * @param rateType     One of the `RateType` values.
    * @return             A `Map` containing the exchange rates against `refCurrency`.
    * @note               Exchange rate for `refCurrency` is set to `1.0`.
    */
  def rates(refCurrency: String, rateType: RateType = Last): Future[Map[String, Double]] = {
    if (!SupportedCurrencies.contains(refCurrency.toUpperCase)) {
      Future.failed(NotSupported("currency", refCurrency))
    } else {
      exchange.ticker zip exchange.eurUsdRate
    }.map { case results =>
      val ticker = results._1 // ticker endpoint does not return any status
      val eurUsdRate = results._2

      eurUsdRate.status match {
        case status if status == 1 =>
          val usdCcy = rateType match {
            case Ask => ticker.ask
            case Bid => ticker.bid
            case High => ticker.high
            case Low => ticker.low
            case _ => ticker.last
          }

          val eurUsd = eurUsdRate.value

          refCurrency match {
            case ref if ref.compareToIgnoreCase(SupportedCurrencies(USD)) == 0 => Map(
              SupportedCurrencies(USD) -> 1.0,
              SupportedCurrencies(EUR) -> 1.0 / eurUsd.get,
              SupportedCurrencies(CCY) -> 1.0 / usdCcy
            )
            case ref if ref.compareToIgnoreCase(SupportedCurrencies(EUR)) == 0 => Map(
              SupportedCurrencies(USD) -> eurUsd.get,
              SupportedCurrencies(EUR) -> 1.0,
              SupportedCurrencies(CCY) -> eurUsd.get / usdCcy
            )
            case _ => Map(
              SupportedCurrencies(USD) -> usdCcy,
              SupportedCurrencies(EUR) -> usdCcy / eurUsd.get,
              SupportedCurrencies(CCY) -> 1.0
            )
          }
        case _ => throw NoRates("EUR-USD", eurUsdRate.message.getOrElse("unknown"))
      }
    }
  }

  /**
    * Enables payout for the object identified by the specified id.
    *
    * @param objectId     The identifier of object
    * @param coinAddress  The recipient coin address.
    * @param currency     The payout currency.
    */
  def enablePayout(objectId: String, coinAddress: String, currency: String = Cryptocurrency): Future[Unit] = {
    exchange.newObject(objectId, coinAddress, currency).map {
      case newObject if newObject.status == 1 => // success
      case newObject => throw CouldNotEnablePayout(
        objectId, coinAddress,
        newObject.message.getOrElse("could not create new recipient object")
      )
    }
  }

  /**
    * Issues the specified payment request.
    *
    * @param paymentRequest The payment request to issue.
    * @return               A `Future` value containing a new invoice.
    */
  def issuePaymentRequest(paymentRequest: PaymentRequest): Future[Invoice] = {
    def findOrder(orderId: String, amount: Coin) = {
      orderService.find(orderId).map {
        case Some(order) => order.status match {
          case Some(status) if status != Processed =>
            paymentRequest.issuerIdentityMode.foreach(identityMode => order.issuerIdentityMode = Some(identityMode))
            order.amount = Some(amount); order
          case _ => throw RequoteNotAllowed(orderId, "order already processed")
        }
        case _ => throw RequoteNotAllowed(orderId, "order not found")
      }
    }

    def createOrder(amount: Coin) = {
      orderService.insert(Order(
        accountId = paymentRequest.accountId,
        refId = Some(paymentRequest.refId),
        orderType = Some(OrderType.PaymentRequest),
        issuerIdentityMode = paymentRequest.issuerIdentityMode,
        amount = Some(amount),
        status = Some(Status(New))
      ))
    }

    val amount = paymentRequest.amount
    rates(amount.currency, Bid).flatMap { rates =>
      val usdRate = rates(SupportedCurrencies(USD))
      (amount.value * usdRate) match {
        case value if value < MinAmount => Future.failed(AmountTooLow("payment", amount.currency, amount.value, MinAmount / usdRate))
        case _ => {
          // implicit precision used by operator ~~
          implicit val precision = Precision(0.00000001)

          // calculate cryptoamount according to current exchange rate
          val cryptoamount = Coin(
            // ~~ rounds half-up to 8 decimals
            (amount.value * rates(Cryptocurrency)) ~~,
            Cryptocurrency,
            Some(amount.currency),
            Some(1.0 / rates(Cryptocurrency))
          )

          val futureOrder: Future[Order] = paymentRequest.orderId match {
            // if order already exists then update it with recalculated invoice amount
            case Some(orderId) => findOrder(orderId, cryptoamount)
            // if order does not exist then create it
            case _ => createOrder(cryptoamount)
          }
          
          futureOrder.flatMap { order =>
            createInvoice(order, paymentRequest.label, paymentRequest.message).flatMap { invoice =>
              if (!order.coinAddress.isDefined) order.coinAddress = Some(invoice.coinAddress)
              order.status = Some(Status(Pending))
              orderService.findAndUpdate(order).map { _ => invoice }
            }.recoverWith { case e =>
              order.status = Some(Status(Failed))
              orderService.findAndUpdate(order).map { _ => throw e }
            }
          }
        }
      }
    }
  }

  /**
    * Buys the specified amount of coins.
    *
    * @param accountId  The identifier of the account the order is associated with.
    * @param amount     The amount of coins to buy.
    * @param refId      An optional reference to the object associated with the order.
    * @param orderId    The identifier of the order that triggered this operation.
    * @return           A `Future` value containing a new buy order.
    * @note             Coins are always deposited into the wallet at the exchange service.
    */
  def buyCoins(
   accountId: String,
   amount: Coin,
   refId: Option[RefId],
   orderId: Option[String] = None
  ): Future[Order] = amount.refCurrency match {
    case Some(refCurrency) => amount.currency match {
      case Cryptocurrency => exchange.ticker.flatMap { ticker =>
        val bid = ticker.ask * (1.0 + (RatePlay / 100.0))
        orderService.insert(Order(
          parentId = orderId,
          accountId = Some(accountId),
          refId = refId,
          orderType = Some(Buy),
          amount = Some(amount),
          status = Some(Status(New))
        )).flatMap { newOrder =>
          exchange.trade("bid", amount.value, bid, amount.currency + refCurrency).flatMap {
            case trade if trade.status == 1 =>
              orderService.findAndUpdate(Order(
                id = newOrder.id,
                peerId = trade.orderId.map(_.toString),
                status = Some(Status(Pending))
              )).map { updatedOrder => updatedOrder.get }
            case trade => throw TradeFailed("purchase", amount.currency, trade.message.getOrElse("unknown"))
          }.recoverWith { case e =>
            orderService.findAndUpdate(Order(
              id = newOrder.id,
              status = Some(Status(Failed))
            )).map { _ => throw e }
          }
        }
      }
      case _ => Future.failed(TradeNotAllowed("purchase", amount.currency, "not a cryptocurrency"))
    }
    case _ => Future.failed(TradeNotAllowed("purchase", amount.currency, "reference currency not defined"))
  }

  /**
    * Sells the specified amount of coins.
    *
    * @param accountId  The identifier of the account the order is associated with.
    * @param amount     The amount of coins to sell.
    * @param refId      An optional reference to the object associated with the order.
    * @param orderId    The identifier of the order that triggered this operation.
    * @return           A `Future` value containing a new sell order.
    * @note             Coins are always withdrawn from the wallet at the exchange service.
    */
  def sellCoins(
   accountId: String,
   amount: Coin,
   refId: Option[RefId],
   orderId: Option[String] = None
  ): Future[Order] = amount.refCurrency match {
    case Some(refCurrency) => amount.currency match {
      case Cryptocurrency => exchange.ticker.flatMap { ticker =>
        val ask = ticker.bid * (1.0 - (RatePlay / 100.0))
        orderService.insert(Order(
          parentId = orderId,
          accountId = Some(accountId),
          refId = refId,
          orderType = Some(Sell),
          amount = Some(amount),
          status = Some(Status(New))
        )).flatMap { newOrder =>
          exchange.trade("ask", amount.value, ask, amount.currency + refCurrency).flatMap {
            case trade if trade.status == 1 =>
              orderService.findAndUpdate(Order(
                id = newOrder.id,
                peerId = trade.orderId.map(_.toString),
                status = Some(Status(Pending))
              )).map { updatedOrder => updatedOrder.get }
            case trade => throw TradeFailed("sale", amount.currency, trade.message.getOrElse("unknown"))
          }.recoverWith { case e =>
            orderService.findAndUpdate(Order(
              id = newOrder.id,
              status = Some(Status(Failed))
            )).map { _ => throw e }
          }
        }
      }
      case _ => Future.failed(TradeNotAllowed("sale", amount.currency, "not a cryptocurrency"))
    }
    case _ => Future.failed(TradeNotAllowed("sale", amount.currency, "reference currency not defined"))
  }

  /**
    * Sends the specified amount of coins to the specified coin address.
    *
    * @param accountId    The identifier of the account the order is associated with.
    * @param amount       The amount of coins to send.
    * @param coinAddress  The recipient coin address.
    * @param refId        A reference to the object associated with the order.
    * @param orderId      The identifier of the order that triggered this operation.
    * @return             A `Future` value containing an order of type `Payment`.
    */
  def sendCoins(
    accountId: String,
    amount: Coin,
    coinAddress: String,
    refId: RefId,
    orderId: Option[String] = None
  ): Future[Order] = amount.currency match {
    case Cryptocurrency => orderService.insert(Order(
        parentId = orderId,
        accountId = Some(accountId),
        refId = Some(refId),
        orderType = Some(Payment),
        coinAddress = Some(coinAddress),
        amount = Some(amount),
        status = Some(Status(New))
      )).flatMap { newOrder => exchange.payoutRequest(refId.value, coinAddress, amount.value, amount.currency).flatMap {
          case defaultResponse if defaultResponse.status == 1 =>
            orderService.findAndUpdate(Order(
              id = newOrder.id,
              status = Some(Status(Pending))
            )).map { updatedOrder => updatedOrder.get}
          case defaultResponse => throw ActionFailed("sending", amount.currency, coinAddress, defaultResponse.message.getOrElse("unknown"))
        }.recoverWith { case e =>
          orderService.findAndUpdate(Order(
            id = newOrder.id,
            status = Some(Status(Failed))
          )).map { _ => throw e }
        }
      }
    case _ => Future.failed(ActionNotAllowed("sending", amount.currency, coinAddress, "not a cryptocurrency"))
  }

  /**
    * Sends the specified amount of coins from the local wallet to the specified coin address.
    *
    * @param accountId    The identifier of the account the order is associated with.
    * @param amount       The amount of coins to forward.
    * @param coinAddress  The recipient coin address.
    * @param refId        A reference to the object associated with the order.
    * @param orderId      The identifier of the order that triggered this operation.
    * @return             A `Future` value containing an order of type `Send`.
    */
  def sendCoinsLocal(
    accountId: String,
    amount: Coin,
    coinAddress: String,
    refId: Option[RefId] = None,
    orderId: Option[String] = None
  ): Future[Order] = amount.currency match {
    case Cryptocurrency => orderService.insert(Order(
        parentId = orderId,
        accountId = Some(accountId),
        refId = refId,
        orderType = Some(Send),
        coinAddress = Some(coinAddress),
        amount = Some(amount),
        status = Some(Status(New))
      )).flatMap { newOrder => Try {
        _walletAppKit.sendCoins(coinAddress, amount.value)
      } match {
        case Success(_) => orderService.findAndUpdate(Order(
            id = newOrder.id,
            status = Some(Status(Pending))
          )).map { updatedOrder => updatedOrder.get}
        case Failure(e) => orderService.findAndUpdate(Order(
            id = newOrder.id,
            status = Some(Status(Failed))
          )).map { _ => 
            throw ActionFailed("sending", amount.currency, coinAddress, e.getMessage)
          }
        }
      }
    case _ => Future.failed(ActionFailed("sending", amount.currency, coinAddress, "not a cryptocurrency"))
  }

  /**
    * Transfers the specified amount of coins to the local wallet.
    *
    * @param accountId    The identifier of the account the order is associated with.
    * @param amount       The amount of coins to transfer.
    * @param refId        A reference to the object associated with the order.
    * @param orderId      The identifier of the order that triggered this operation.
    * @return             A `Future` value containing an order of type `Transfer`.
    */
  def transferCoins(
    accountId: String,
    amount: Coin,
    refId: Option[RefId] = None,
    orderId: Option[String] = None
  ): Future[Order] = amount.currency match {
    case Cryptocurrency => orderService.insert(Order(
        parentId = orderId,
        accountId = Some(accountId),
        refId = refId,
        orderType = Some(Transfer),
        amount = Some(amount),
        status = Some(Status(New))
      )).flatMap { newOrder => exchange.cryptoWithdraw(amount.value, amount.currency).flatMap {
          case defaultResponse if defaultResponse.status == 1 =>
            orderService.findAndUpdate(Order(
              id = newOrder.id,
              status = Some(Status(Pending))
            )).map { updatedOrder => updatedOrder.get}
          case defaultResponse => throw ActionFailed(
            "transferring", amount.currency,
            _walletAppKit.wallet.currentReceiveAddress.toString, defaultResponse.message.getOrElse("unknown")
          )
        }.recoverWith { case e =>
          orderService.findAndUpdate(Order(
            id = newOrder.id,
            status = Some(Status(Failed))
          )).map { _ => throw e }
        }
      }
    case _ => Future.failed(ActionNotAllowed(
      "transferring", amount.currency,
      _walletAppKit.wallet.currentReceiveAddress.toString, "not a cryptocurrency"
    ))
  }

  /**
    * Processes transaction acknowledgements coming from the exchange service.
    *
    * @param ack  The transaction ack sent by the exchange service when coins are
    *             either deposited or withdrawn.
    * @return     A `Future` value containing a new application transaction.
    * @note       Transaction acknowledges are provided only when cryptocoins
    *             come in or out of the exchange account.
    */
  def processAck(ack: Ack): Future[Transaction] = {
    val amount = Coin(ack.amount, ack.currency)
    val transaction = Transaction(
      None, // transaction id
      None, // order id
      TransactionType(ack.transactionType),
      None, // transaction hash
      Some(ack.address),
      amount,
      None // transaction fee
    )

    orderService.findAndUpdate(
      Json.obj("coinAddress" -> ack.address),
      Json.obj("status.value" -> Processed)
    ).flatMap {
      case Some(order) =>
        // update amount with reference currency and exchange rate
        // note: exchange rate here is just indicative and is used by child transactions
        //       to re-calculate the original pledge amount
        amount.refCurrency = order.amount.get.refCurrency
        amount.rate = order.amount.get.rate

        // update transaction with order id and amount
        transaction.orderId = order.id
        transaction.amount = amount

        transactionService.insert(transaction).map {
          case newTransaction if newTransaction.transactionType == Withdrawal => newTransaction
          case newTransaction if newTransaction.transactionType == Deposit =>
            messageBroker.producers(XchangeDeposit) ! JsMessage(newTransaction.asJson)
            newTransaction
        }
      case _ => transactionService.insert(transaction)
    }
  }

  /**
    * Creates an invoice for the specified payment request order.
    *
    * @param order    The payment request order to create the invoice for.
    * @param label    The label associated with the payment request.
    * @param message  The message that describes the payment request.
    * @return         A `Future` value containing a new invoice.
    */
  private def createInvoice(
    order: Order,
    label: Option[String] = None,
    message: Option[String] = None
  ): Future[Invoice] = {
    for {
      coinAddress <- order.coinAddress match {
        case Some(coinAddress) => Future.successful(coinAddress)
        case _ => exchange.newPayment(Cryptocurrency).map {
          case recipientAddress if recipientAddress.status == 1 => recipientAddress.address.get
          case recipientAddress => throw CouldNotCreateInvoice(recipientAddress.message.getOrElse("unknown"))
        }
      }
      invoice <- Future {
        val amount = order.amount.get.value
        val uri = WalletAppKit.bitcoinUri(
          coinAddress, amount,
          label getOrElse null,
          message getOrElse null
        )
        Invoice(
          orderId = order.id.get,
          coinAddress, Coin(amount, order.amount.get.currency),
          label, message, QrCode(uri, QrCodeSize),
          DateTime.now(DateTimeZone.UTC).plusMinutes(InvoiceValidity)
        )
      }
    } yield invoice
  }

  /**
    * Processes pending trade orders.
    * @return  The number of orders processed.
    * @note    For trade orders cryptocoins always come in or out of the exchange account.
    */
  private[pay] def processTradeOrders: Future[Int] = {

    def processOrders(count: Int): Future[Int] = {
      for {
        // get pending orders in chunk of `DefaultPerPage`
        orders <- orderService.find(
          Json.obj("$or" -> Json.arr(Json.obj("type" -> Buy), Json.obj("type" -> Sell)), "status.value" -> Pending),
          None, None, 0, DefaultPerPage
        )
        orderProcessedCount <- orders.isEmpty match {
          case true => Future.successful(count)
          case false => for {
            _ <- Future.sequence(orders.collect { case order if order.peerId.isDefined =>
              createTransactions(order)
            })

            // invoke processOrders recursively until there are no orders left
            orderProcessedCount <- orders.length match {
              case l if l == DefaultPerPage => processOrders(count + l)
              case _ => Future.successful(count)
            }
          } yield orderProcessedCount
        }
      } yield orderProcessedCount
    }

    def createTransactions(order: Order): Future[List[Transaction]] = {
      val transactionType = if (order.orderType.get == Buy) Deposit else Withdrawal

      for {
        // get all the peer transactions generated by the exchange service
        peerTransactions <- exchange.orderTransactions(order.peerId.get)

        // for each peer transaction create a new application transaction
        transactions <- Future.sequence(peerTransactions.map { peerTransaction =>
          for {
            // change order status from pending to processed
            _ <- orderService.findAndUpdate(
              Json.obj("id" -> order.id),
              Json.obj("status.value" -> Processed)
            )

            // get reference exchange rate
            refRate <- rates(SupportedCurrencies(USD), if (transactionType == Deposit) Bid else Ask).map {
              _(order.amount.get.refCurrency.get)
            }
            
            // create new transaction
            transaction <- transactionService.insert(Transaction(
              None,
              order.id,
              transactionType,
              None, // no transaction hash
              None, // no recipient coin address
              Coin( // gross amount not including fee
                peerTransaction.amount,
                Cryptocurrency,
                order.amount.get.refCurrency,
                Some(peerTransaction.price * refRate)
              ),
              // to calculate net amount add fee to amount when
              // buying coins or subtract fee when selling coins
              if (peerTransaction.fee > 0) Some(Coin(
                peerTransaction.amount * peerTransaction.fee,
                Cryptocurrency,
                order.amount.get.refCurrency,
                Some(peerTransaction.price * refRate)
              )) else (None)
            ))
          } yield transaction
        })

        // publish new transactions and let registered consumers process them
        _ <- Future { transactionType match {
          case Deposit => messageBroker.producers(XchangeDepositOnTrade) ! JsMessage(Json.toJson(transactions))
          case Withdrawal => messageBroker.producers(XchangeWithdrawalOnTrade) ! JsMessage(Json.toJson(transactions))
        }}
      } yield transactions
    }

    processOrders(0)
  }

  /**
    * Expires payment requests stick around for too long.
    * @return The number of payment requests expired.
    */
  private[pay] def expirePaymentRequests: Future[Int] = {
    val dateTime = DateTime.now(DateTimeZone.UTC).minusMinutes(InvoiceValidity).getMillis

    orderService.update(
      Json.obj(
        "type" -> OrderType.PaymentRequest,
        "status.value" -> Pending,
        "$lt" -> Json.obj("status.timestamp" -> dateTime)
      ),
      Json.obj("status" -> Status(Expired))
    )
  }

  /**
    * Discards payment requests expired for too long.
    * @return The number of payment requests discarded.
    */
  private[pay] def discardExpiredPaymentRequests: Future[Int] = {
    val dateTime = DateTime.now(DateTimeZone.UTC).minusDays(2).getMillis

    orderService.remove(Json.obj(
      "type" -> OrderType.PaymentRequest,
      "status.value" -> Expired,
      "$lte" -> Json.obj("status.timestamp" -> dateTime)
    ))
  }

  private class WalletListener extends org.bitcoinj.core.AbstractWalletEventListener {

    import org.bitcoinj.core.{Context, Wallet, Transaction => JTransaction, Coin => JCoin, TransactionConfidence}
    import org.bitcoinj.wallet.DefaultCoinSelector

    private class ConfidenceListener(
      val transaction: JTransaction,      // bitcoin transaction
      val depositTransaction: Transaction // deposit transaction
    ) extends TransactionConfidence.Listener {

      /** Called when the level of transaction confidence has changed. */
      def onConfidenceChanged(confidence: TransactionConfidence, reason: TransactionConfidence.Listener.ChangeReason) {
        Context.propagate(_walletAppKit.wallet.getContext)
        // ensure the bitcoin transaction has been confirmed and is spendable
        if (DefaultCoinSelector.isSelectable(transaction)) {
          // publish new transaction and let registered consumers process it
          messageBroker.producers(LocalDeposit) ! JsMessage(depositTransaction.asJson)
          confidence.removeEventListener(this)
        }
      }
    }

    /** Called when a transaction is seen that sends coins ''to'' the specified wallet. */
    override def onCoinsReceived(
      wallet: Wallet,
      transaction: JTransaction,
      prevBalance: JCoin,
      newBalance: JCoin
    ) {
      val amount = WalletAppKit.fromNanoCoins(transaction.getValue(wallet).getValue)

      for {
        // get oldest pending transfer order, if any
        order <- orderService.findWithPrecision(
          Json.obj("type" -> Transfer, "status.value" -> Pending, "amount.value" -> amount),
          None, Some(Json.obj("$asc" -> Json.arr("status.timestamp"))), 0, 1
        ).map(_.headOption)

        // create new transaction regardless of whether or not a transfer order exists
        depositTransaction <- transactionService.insert(Transaction(
          None,
          order.flatMap(_.id),
          Deposit,
          Some(transaction.getHashAsString),
          Some(wallet.currentReceiveAddress.toString),
          Coin(
            amount,
            Cryptocurrency,
            order.flatMap(_.amount.get.refCurrency),
            order.flatMap(_.amount.get.rate)
          ),
          Some(Coin(
            WalletAppKit.fromNanoCoins(transaction.getFee.getValue),
            Cryptocurrency,
            order.flatMap(_.amount.get.refCurrency),
            order.flatMap(_.amount.get.rate)
          ))
        ))

        // if transfer order exists, then change its status from pending to processed
        _ <- orderService.findAndUpdate(
          Json.obj("id" -> order.flatMap(_.id)),
          Json.obj("status.value" -> Processed)
        ) if order.isDefined

        // if transfer order exists, then add listener to be notified when transaction
        // confidence changes and coins are spendable
        _ <- Future {
          Context.propagate(_walletAppKit.wallet.getContext)
          transaction.getConfidence().addEventListener(new ConfidenceListener(transaction, depositTransaction))
        } if order.isDefined
      } yield 1
    }

    /** Called when a transaction is seen that sends coins ''from'' the specified wallet. */
    override def onCoinsSent(
      wallet: Wallet,
      transaction: JTransaction,
      prevBalance: JCoin,
      newBalance: JCoin
    ) {
      val amount = transaction.getValue(wallet).getValue
      val fee = transaction.getFee.getValue

      val transactionAmount = WalletAppKit.fromNanoCoins(math.abs(amount))
      val orderAmount = WalletAppKit.fromNanoCoins(math.abs(amount + fee))

      for {
        // get oldest pending send order, if any
        order <- WithRetry(60000) {
          orderService.findWithPrecision(
            Json.obj("type" -> Send, "status.value" -> Pending, "amount.value" -> orderAmount),
            None, Some(Json.obj("$asc" -> Json.arr("status.timestamp"))), 0, 1
          ).map(_.headOption)
        }

        // create new transaction regardless of whether or not a send order exists
        _ <- transactionService.insert(Transaction(
          None,
          order.flatMap(_.id),
          Withdrawal,
          Some(transaction.getHashAsString),
          order.flatMap(_.coinAddress),
          Coin(
            transactionAmount,
            Cryptocurrency,
            order.flatMap(_.amount.get.refCurrency),
            order.flatMap(_.amount.get.rate)
          ),
          Some(Coin(
            WalletAppKit.fromNanoCoins(fee),
            Cryptocurrency,
            order.flatMap(_.amount.get.refCurrency),
            order.flatMap(_.amount.get.rate)
          ))
        ))

        // if send order exists, then change its status from pending to processed
        _ <- orderService.findAndUpdate(
          Json.obj("id" -> order.flatMap(_.id)),
          Json.obj("status.value" -> Processed)
        ) if order.isDefined
      } yield 1
    }
  }

  /**
    * Exchange service API.
    */
  private[this] object exchange {

    import play.api.http.Status._
    import play.api.libs.ws._

    private final val RequestTimeout = 5000
    private final val RequestContentType = "application/x-www-form-urlencoded"

    // exchange api endpoints
    private final val XchangeTickerUrl = peers("xchange").endPoint("ticker")
    private final val XchangeRatesUrl = peers("xchange").endPoint("rates")
    private final val XchangeNewObjectUrl = peers("xchange").endPoint("newObject")
    private final val XchangeNewPaymentUrl = peers("xchange").endPoint("newPayment")
    private final val XchangePayoutRequestUrl = peers("xchange").endPoint("payoutRequest")
    private final val XchangeBalanceUrl = peers("xchange").endPoint("balance")
    private final val XchangeCryptoWithdrawUrl = peers("xchange").endPoint("cryptoWithdraw")
    private final val XchangeFiatWithdrawUrl = peers("xchange").endPoint("fiatWithdraw")
    private final val XchangeTradeUrl = peers("xchange").endPoint("trade")
    private final val XchangeOrderDetailsUrl = peers("xchange").endPoint("orderDetails")
    private final val XchangeTransactionsUrl = peers("xchange").endPoint("transactions")
    private final val XchangeOrderTransactionsUrl = peers("xchange").endPoint("orderTransactions")

    // exchange credentials
    private final val XchangeClientId = configuration.getString("pay.xchange.clientId").getOrElse("")
    private final val XchangeBankId = configuration.getString("pay.xchange.bankId").getOrElse("")
    private final val XchangeApiKey = configuration.getString("pay.xchange.apiKey").getOrElse("")
    private final val XchangeApiSecret = Secret(configuration.getString("pay.xchange.apiSecret").getOrElse(""))

    /**
      * Gets market data.
      * @return A `Future` value containing an instance of the [[Ticker]] class.
      */
    def ticker: Future[Ticker] = {
      WS.url(XchangeTickerUrl).withRequestTimeout(RequestTimeout).get().map {
        case response if response.status != OK => throw XchangeError(XchangeTickerUrl, response.statusText)
        case response => {
          Ticker(Json.parse(response.body)) match {
            case JsSuccess(ticker, _) => ticker
            case JsError(errors) => throw InvalidResponse(XchangeTickerUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Gets EUR/USD exchange rate.
      * @return A `Future` value containing an instance of the [[EurUsdRate]] class.
      */
    def eurUsdRate: Future[EurUsdRate] = {
      WS.url(XchangeRatesUrl).withRequestTimeout(RequestTimeout).get().map {
        case response if response.status != OK => throw XchangeError(XchangeRatesUrl, response.statusText)
        case response => {
          EurUsdRate(Json.parse(response.body)) match {
            case JsSuccess(eurUsdRate, _) => eurUsdRate
            case JsError(errors) => throw InvalidResponse(XchangeRatesUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Creates a new object waiting for a payment.
      *
      * @param objectId The identifier of the object.
      * @param address  The recipient coin address.
      * @param currency The cryptocurrency of the payment.
      * @return         A `Future` value containing an instance of the [[NewObject]] class.
      */
    def newObject(objectId: String, address: String, currency: String): Future[NewObject] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeNewObjectUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeNewObjectUrl, nonce)),
          "id" -> Seq(objectId),
          "address" -> Seq(address),
          "currency" -> Seq(currency)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeNewObjectUrl, response.statusText)
        case response => {
          NewObject(Json.parse(response.body)) match {
            case JsSuccess(newObject, _) => newObject
            case JsError(errors) => throw InvalidResponse(XchangeNewObjectUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Creates a new recipient address for a payment request.
      *
      * @param currency The cryptocurrency to create the recipient address for.
      * @return         A `Future` value containing an instance of the [[NewPayment]] class.
      */
    def newPayment(currency: String): Future[NewPayment] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeNewPaymentUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeNewPaymentUrl, nonce)),
          "currency" -> Seq(currency)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeNewPaymentUrl, response.statusText)
        case response => {
          NewPayment(Json.parse(response.body)) match {
            case JsSuccess(newPayment, _) => newPayment
            case JsError(errors) => throw InvalidResponse(XchangeNewPaymentUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Withdraws the specified amount and sends it to the object identified
      * by the specified id.
      *
      * @param objectId The identifier of the object to sent the payment to.
      * @param address  The recipient coin address.
      * @param amount   The amount in cryptocoin units.
      * @param currency The cryptocurrency of `amount`.
      * @return         A `Future` value containing an instance of the [[DefaultResponse]] class.
      * @note           The object identified by `objectId` should have been
      *                 created by a previous call to `newObject`.
      */
    def payoutRequest(objectId: String, address: String, amount: Double, currency: String): Future[DefaultResponse] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangePayoutRequestUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangePayoutRequestUrl, nonce)),
          "id" -> Seq(objectId),
          "address" -> Seq(address),
          "amount" -> Seq(amount.toString),
          "currency" -> Seq(currency)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangePayoutRequestUrl, response.statusText)
        case response => {
          DefaultResponse(Json.parse(response.body)) match {
            case JsSuccess(defaultResponse, _) => defaultResponse
            case JsError(errors) => throw InvalidResponse(XchangePayoutRequestUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Gets the current balance.
      * @return A `Future` value containing an instance of the [[Balance]] class.
      */
    def balance: Future[Balance] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeBalanceUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeBalanceUrl, nonce))
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeBalanceUrl, response.statusText)
        case response => {
          Balance(Json.parse(response.body)) match {
            case JsSuccess(balance, _) => balance
            case JsError(errors) => throw InvalidResponse(XchangeBalanceUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Withdraws the specified amount from the backing wallet.
      *
      * @param amount   The amount in cryptocoin units.
      * @param currency The cryptocurrency of `amount`.
      * @return         A `Future` value containing an instance of the [[DefaultResponse]] class.
      */
    def cryptoWithdraw(amount: Double, currency: String): Future[DefaultResponse] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeCryptoWithdrawUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeCryptoWithdrawUrl, nonce)),
          "amount" -> Seq(amount.toString),
          "currency" -> Seq(currency)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeCryptoWithdrawUrl, response.statusText)
        case response => {
          DefaultResponse(Json.parse(response.body)) match {
            case JsSuccess(defaultResponse, _) => defaultResponse
            case JsError(errors) => throw InvalidResponse(XchangeCryptoWithdrawUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Withdraws the specified amount from the backing bank.
      *
      * @param amount   The amount in fiat money.
      * @param currency The currency of `amount`.
      * @return         A `Future` value containing an instance of the [[DefaultResponse]] class.
      */
    def fiatWithdraw(amount: Double, currency: String): Future[DefaultResponse] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeFiatWithdrawUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeFiatWithdrawUrl, nonce)),
          "amount" -> Seq(amount.toString),
          "currency" -> Seq(currency),
          "bank_id" -> Seq(XchangeBankId)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeFiatWithdrawUrl, response.statusText)
        case response => {
          DefaultResponse(Json.parse(response.body)) match {
            case JsSuccess(defaultResponse, _) => defaultResponse
            case JsError(errors) => throw InvalidResponse(XchangeFiatWithdrawUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Submits a trade order.
      *
      * @param type   Either 'buy' or 'ask'.
      * @param amount The number of units to trade.
      * @param price  The price per unit.
      * @param pair   The currency pair (e.g. BTCUSD).
      * @return       A `Future` value containing an instance of the [[Trade]] class.
      */
    def trade(`type`: String, amount: Double, price: Double, pair: String): Future[Trade] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeTradeUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeTradeUrl, nonce)),
          "type" -> Seq(`type`),
          "amount" -> Seq(amount.toString),
          "price" -> Seq(price.toString),
          "pair" -> Seq(pair)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeTradeUrl, response.statusText)
        case response => {
          Trade(Json.parse(response.body)) match {
            case JsSuccess(trade, _) => trade
            case JsError(errors) => throw InvalidResponse(XchangeTradeUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Gets details about the order identified by the specified id.
      *
      * @param orderId  The identifier of the order to get details for.
      * @return         A `Future` value containing an instance of the [[OrderDetails]] class.
      */
    def orderDetails(orderId: String): Future[OrderDetails] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeOrderDetailsUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeOrderDetailsUrl, nonce)),
          "order_id" -> Seq(orderId)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeOrderDetailsUrl, response.statusText)
        case response => {
          OrderDetails(Json.parse(response.body)) match {
            case JsSuccess(orderDetails, _) => orderDetails
            case JsError(errors) => throw InvalidResponse(XchangeOrderDetailsUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Gets the specified number of transactions, starting from the specified
      * offset, ordered either ascending or descending.
      *
      * @param offset The number of transactions to skip.
      * @param limit  The maximum number of transactions to fetch.
      * @param sort   Either `asc` or `desc`.
      * @return       A `Future` value containing a list of transactions.
      */
    def transactions(offset: Int = 0, limit: Int = 100, sort: String = "desc"): Future[List[XTransaction]] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeTransactionsUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeTransactionsUrl, nonce)),
          "offset" -> Seq(offset.toString),
          "limit" -> Seq(limit.toString),
          "sort" -> Seq(sort)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeTransactionsUrl, response.statusText)
        case response => {
          Json.parse(response.body).validate[List[XTransaction]] match {
            case JsSuccess(transactions, _) => transactions
            case JsError(errors) => throw InvalidResponse(XchangeTransactionsUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Gets all the transactions associated with the order identified by the specified id.
      *
      * @param orderId  The identifier of the order to get the transactions for.
      * @return         A `Future` value containing a list of transactions.
      */
    def orderTransactions(orderId: String): Future[List[XTransaction]] = {
      val nonce = DateTime.now(DateTimeZone.UTC).getMillis.toString
      WS.url(XchangeOrderTransactionsUrl)
        .withHeaders(("Content-Type", RequestContentType))
        .withRequestTimeout(RequestTimeout)
        .post(Map(
          "api_key" -> Seq(XchangeApiKey),
          "nonce" -> Seq(nonce),
          "base64_hmac" -> Seq(signRequest(XchangeOrderTransactionsUrl, nonce)),
          "order_id" -> Seq(orderId)
      )).map {
        case response if response.status != OK => throw XchangeError(XchangeOrderTransactionsUrl, response.statusText)
        case response => {
          Json.parse(response.body).validate[List[XTransaction]] match {
            case JsSuccess(transactions, _) => transactions
            case JsError(errors) => throw InvalidResponse(XchangeOrderTransactionsUrl, Json.toJson(errors))
          }
        }
      }
    }

    /**
      * Signs a request to the exchange service.
      *
      * @param url    The request URL.
      * @param nonce  An arbitray value used once to prevent reply attacks.
      * @return       The HMAC digest encoded in Base64.
      */
    private def signRequest(url: String, nonce: String): String = {
      XchangeApiSecret.sign(url + nonce + XchangeClientId + XchangeApiKey, "HmacSHA256") match {
        case Success(signed) => signed
        case Failure(e) => throw e
      }
    }
  }
}
