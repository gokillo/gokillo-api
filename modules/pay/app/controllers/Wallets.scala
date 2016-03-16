/*#
  * @file Wallets.scala
  * @begin 27-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.pay

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.collection.JavaConversions._
import java.util.TreeSet
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import org.bitcoinj.core.{Context, Transaction, Wallet}
import org.bitcoinj.core.Wallet.BalanceType
import controllers.auth.Security
import services.common.CommonErrors
import services.auth.AuthErrors
import services.pay.PayErrors
import services.pay.PayGateway
import services.pay.WalletAppKit._
import utils.common.env._
import utils.common.typeExtensions._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.pay.{Coin, CoinNet, WalletInfo, WalletTransaction}
import models.pay.Coin._
import models.pay.CoinNet._

@Api(
  value = "/pay/wallets",
  description = "Deposit coins, withdraw coins, and lookup local wallets",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Wallets extends Controller with Security {

  protected val errors = new CommonErrors with AuthErrors with PayErrors {}

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the wallets",
    response = classOf[models.pay.api.WalletInfo],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No wallets found"),
    new ApiResponse(code = 500, message = "Error processing list wallets request")))
  def list = Authorized("list") { token =>
    Action.async { implicit request =>
      Future {
        Context.propagate(PayGateway.walletAppKit.wallet.getContext)

        val wallets = for {
          file <- PayGateway.walletAppKit.directory.listFiles if file.getName endsWith ".wallet"
        } yield {
          val wallet = Wallet.loadFromFile(file)

          val description = wallet.getDescription match {
            case description if description != null => Some(description)
            case _ => None
          }

          val pendingTransactions = wallet.getPendingTransactions match {
            case pending if pending.size > 0 =>
              val transactions = new TreeSet[Transaction](Transaction.SORT_TX_BY_UPDATE_TIME)
              transactions.addAll(pending)
              val results = for (transaction <- transactions) yield {
                WalletTransaction(
                  fromNanoCoins(transaction.getValueSentFromMe(wallet).getValue),
                  fromNanoCoins(transaction.getValueSentToMe(wallet).getValue)
                )
              }; Some(results.toList)
            case _ => None
          }

          WalletInfo(
            file.getName,
            description,
            wallet.currentReceiveAddress.toString,
            fromNanoCoins(wallet.getBalance(BalanceType.ESTIMATED).getValue),
            fromNanoCoins(wallet.getBalance(BalanceType.AVAILABLE_SPENDABLE).getValue),
            fromNetParams(PayGateway.walletAppKit.params),
            wallet.getVersion,
            wallet.isEncrypted,
            pendingTransactions
          ).asJson
        }

        Ok(success(Json.obj("wallets" -> Json.toJson(wallets))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list wallets"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "sendCoins",
    value = "Sends an amount of coins from the active local wallet to a given coin address",
    response = classOf[String])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid coin address"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No wallets found"),
    new ApiResponse(code = 422, message = "Amount data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing send coins request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "amount",
    value = "The amount of coins to send",
    required = true,
    dataType = "models.pay.api.Coin",
    paramType = "body")))
  def sendCoins(
    @ApiParam(
      name = "coinAddress",
      value = "The recipient coin address",
      required = true)
    @PathParam("coinAddress")
    coinAddress: String
  ) = Authorized("sendCoins") { token =>
    Action.async(parse.json) { implicit request =>
      if (coinAddress.isCoinAddress) {
        request.body.validate[Coin](coinFormat).fold(
          valid = { coin =>
            PayGateway.sendCoinsLocal(token.account, coin, coinAddress).flatMap { order =>
              Logger.debug(s"sent ${coin.currency} ${coin.value} to coin address $coinAddress")
              Logger.debug(s"created send order ${order.id.get}")
              Future.successful(Created(success).withHeaders(
                LOCATION -> s"""${localhost.toString("/pay/orders/" + order.id.get)}"""
              ))
            }.recover { case NonFatal(e) =>
              errors.toResult(e, Some(s"could not send coins to coin address $coinAddress"))
            }
          },
          invalid = { validationErrors =>
            val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
            Future.successful(errors.toResult(e, Some("error parsing send coins request")))
          }
        )
      } else Future.successful(errors.toResult(PayErrors.InvalidCoinAddress(coinAddress), None))
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "transferCoins",
    value = "Transfers an amount of coins from the Exchange to the active local wallet",
    response = classOf[String])
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No wallets found"),
    new ApiResponse(code = 422, message = "Amount data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing transfer coins request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "amount",
    value = "The amount of coins to transfer",
    required = true,
    dataType = "models.pay.api.Coin",
    paramType = "body")))
  def transferCoins = Authorized("transferCoins") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Coin](coinFormat).fold(
        valid = { coin =>
          PayGateway.transferCoins(token.account, coin).flatMap { order =>
            Logger.debug(s"transferred ${coin.currency} ${coin.value} to active local wallet")
            Logger.debug(s"created transfer order ${order.id.get}")
            Future.successful(Created(success).withHeaders(
              LOCATION -> s"""${localhost.toString("/pay/orders/" + order.id.get)}"""
            ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not transfer coins to active local wallet"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing transfer coins request")))
        }
      )
    }
  }
}
