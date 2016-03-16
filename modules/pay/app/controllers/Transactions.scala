/*#
  * @file Transactions.scala
  * @begin 27-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.pay

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import brix.crypto.Secret
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors}
import services.auth.{AuthErrors, ApiConsumerDaoServiceComponent}
import services.auth.mongo.MongoApiConsumerDaoComponent
import services.pay._
import services.pay.mongo._
import services.pay.PayGateway._
import utils.common.typeExtensions._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._

@Api(
  value = "/pay/transactions",
  description = "Transaction processing and lookup",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Transactions extends Controller with Security {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors with PayErrors {}

  protected val apiConsumerService: ApiConsumerDaoServiceComponent#ApiConsumerDaoService = new ApiConsumerDaoServiceComponent
    with MongoApiConsumerDaoComponent {
  }.daoService.asInstanceOf[ApiConsumerDaoServiceComponent#ApiConsumerDaoService]

  protected val transactionService: TransactionDaoServiceComponent#TransactionDaoService = new TransactionDaoServiceComponent
    with MongoTransactionDaoComponent {
  }.daoService.asInstanceOf[TransactionDaoServiceComponent#TransactionDaoService]

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds a transaction",
    response = classOf[models.pay.api.Transaction])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid transaction id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Transaction not found"),
    new ApiResponse(code = 500, message = "Error processing find transaction request")))
  def find(
    @ApiParam(
      name = "transactionId",
      value = "The id of the transaction to find",
      required = true)
    @PathParam("transactionId")
    transactionId: String
  ) = Authorized("find") { token =>
    Action.async { implicit request =>
      transactionService.find(transactionId).map {
        case Some(transaction) => Ok(success(Json.obj("transaction" -> transaction.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("transaction", transactionId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find transaction $transactionId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findBySource",
    value = "Finds a transaction by invoice or payment id",
    response = classOf[models.pay.api.Transaction])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid invoice or payment id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Transaction not found"),
    new ApiResponse(code = 500, message = "Error processing find transaction by source request")))
  def findBySource(
    @ApiParam(
      name = "sourceId",
      value = "The id of the invoice or payment associated with the transaction to find",
      required = true)
    @PathParam("sourceId")
    sourceId: String
  ) = Authorized("findBySource") { token =>
    Action.async { implicit request =>
      transactionService.find(
        Json.obj(),
        Some(Json.obj("sourceId" -> sourceId)), None, 0, 1
      ).map { _.headOption match {
        case Some(transaction) => Ok(success(Json.obj("transaction" -> transaction.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("transaction associated with source", sourceId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find transaction associated with source $sourceId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the transactions",
    response = classOf[models.pay.api.Transaction],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No transactions found"),
    new ApiResponse(code = 500, message = "Error processing list transactions request")))
  def list(
    @ApiParam(
      name = "page",
      value = "The page to retrieve (0-based)",
      required = true)
    @PathParam("page")
    page: Int,

    @ApiParam(
      name = "perPage",
      value = "The number of results per page",
      required = true)
    @PathParam("perPage")
    perPage: Int
  ) = Authorized("list") { token =>
    Action.async { implicit request =>
      transactionService.find(
        Json.obj(),
        None,
        Some(Json.obj("$asc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { transactions =>
        if (transactions.isEmpty) errors.toResult(CommonErrors.EmptyList("transactions"), None)
        else Ok(success(Json.obj("transactions" -> Json.toJson(transactions))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list transactions"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "callback",
    value = "Called by the exchange service when coins come in our go out",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 403, message = "Request could not be validated"),
    new ApiResponse(code = 500, message = "Error processing callback request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "ack",
    value = "The transaction ack data",
    required = true,
    dataType = "models.pay.xchange.api.Ack",
    paramType = "body")))
  def callback = Action.async(parse.json) { implicit request =>

    import models.pay.xchange.Ack
    import Ack._

    apiConsumerService.find(
      Json.obj("name" -> XchangeName),
      Some(Json.obj("$include" -> Json.arr("apiKey"))),
      None,
      0, 1
    ).flatMap { _.headOption match {
      case Some(apiConsumer) =>
        val hash = (request.body \ "hash") match {
          case _: JsUndefined => "?"
          case js => js.as[JsString].value
        }
        val data = (request.body \ "data") match {
          case _: JsUndefined => request.body.toString
          case js => js.sort.toString
        }

        Secret(apiConsumer.apiKey.get).sign(data, "HmacSHA512") match {
          case Success(signed) if signed == hash => request.body.validate[Ack](ackFormat).fold(
            valid = { ack => processAck(ack).map { _ =>
              Ok(success) 
            }},
            invalid = { validationErrors =>
              Future.failed(CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors)))
            })
          case Success(signed) => Future.failed(AuthErrors.AuthenticationViolated("app", apiConsumer.id.get))
          case Failure(e) => Future.failed(e)
        }
      case None => Future.failed(AuthErrors.NotRegistered("app", XchangeName))
    }}.recover { case NonFatal(e) =>
      errors.toResult(e, Some(s"could not process callback request"))
    }
  }
}
