/*#
  * @file Orders.scala
  * @begin 8-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.pay

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors}
import services.auth.AuthErrors
import services.pay._
import services.pay.mongo._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import utils.common.typeExtensions._
import models.common.{Id, RefId}
import models.common.RefId._
import models.auth.{IdentityMode, Role}
import models.auth.IdentityMode._
import models.pay._
import models.pay.Order._
import models.pay.PaymentRequest._
import models.pay.OrderType._
import models.pay.OrderStatus._

@Api(
  value = "/pay/orders",
  description = "Request money, send money, and lookup orders",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Orders extends Controller with Security {

  protected val errors = new CommonErrors with DaoErrors with AuthErrors with PayErrors {}

  protected val orderService: OrderDaoServiceComponent#OrderDaoService = new OrderDaoServiceComponent
    with MongoOrderDaoComponent {
  }.daoService.asInstanceOf[OrderDaoServiceComponent#OrderDaoService]

  protected val transactionService: TransactionDaoServiceComponent#TransactionDaoService = new TransactionDaoServiceComponent
    with MongoTransactionDaoComponent {
  }.daoService.asInstanceOf[TransactionDaoServiceComponent#TransactionDaoService]

  @ApiOperation(
    httpMethod = "POST",
    nickname = "issuePaymentRequest",
    value = "Issues a payment request and generates the associated order",
    response = classOf[String])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid payment request data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 422, message = "Payment request data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing issue payment request request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "paymentRequest",
    value = "The payment request data",
    required = true,
    dataType = "models.pay.api.PaymentRequest",
    paramType = "body")))
  def issuePaymentRequest = Authorized("issuePaymentRequest") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[PaymentRequest](paymentRequestFormat).fold(
        valid = { paymentRequest =>
          PayGateway.issuePaymentRequest(paymentRequest).flatMap { invoice =>
            Logger.debug(s"created payment request order ${invoice.orderId}")
            Future.successful(Created(success(Json.obj("invoice" -> invoice.asJson))).withHeaders(
              LOCATION -> s"""${requestUri.truncateBefore("paymentRequest")}${invoice.orderId}"""
            ))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not issue payment request"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing issue payment request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setIssuerIdentityMode",
    value = "Sets the identity mode of the issuer of an order",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid order id or invalid identity mode"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Order not found"),
    new ApiResponse(code = 500, message = "Error processing set issuer identity mode request")))
  def setIssuerIdentityMode(
    @ApiParam(
      name = "orderId",
      value = "The id of the order to set the issuer identity mode for",
      required = true)
    @PathParam("orderId")
    orderId: String,

    @ApiParam(
      name = "identityMode",
      value = "The identity mode to set",
      allowableValues = "anonym,username,fullName,companyName",
      required = true)
    @PathParam("identityMode")
    identityMode: String
  ) = Authorized("setIssuerIdentityMode") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("id" -> orderId, "type" -> OrderType.PaymentRequest, "status.value" -> Pending)
      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("accountId" -> token.account)
      }

      orderService.findAndUpdate(
        selector,
        Json.obj("issuerIdentityMode" -> IdentityMode(identityMode))
      ).map {
        case Some(_) =>
          Logger.debug(s"issuer identity mode of pending payment request $orderId set to $identityMode")
          Ok(success)
        case _ => errors.toResult(CommonErrors.NotFound("pending payment request", orderId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set issuer identity mode of payment request $orderId to $identityMode"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "find",
    value = "Finds an order",
    response = classOf[models.pay.api.Order])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid order id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "Order not found"),
    new ApiResponse(code = 500, message = "Error processing find order request")))
  def find(
    @ApiParam(
      name = "orderId",
      value = "The id of the order to find",
      required = true)
    @PathParam("orderId")
    orderId: String
  ) = Authorized("find") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("id" -> orderId)
      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("accountId" -> token.account)
      }

      orderService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        None, 0, 1
      ).map { _.headOption match {
        case Some(order) => Ok(success(Json.obj("order" -> order.asJson)))
        case _ => errors.toResult(CommonErrors.NotFound("order", orderId), None)
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find order $orderId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "list",
    value = "Lists all the orders",
    response = classOf[models.pay.api.Order],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No orders found"),
    new ApiResponse(code = 500, message = "Error processing list orders request")))
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
      val selector = if (!token.roles.contains(Role.Superuser.id)) {
        Json.obj("accountId" -> token.account)
      } else Json.obj()

      orderService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { orders =>
        if (orders.isEmpty) errors.toResult(CommonErrors.EmptyList("orders"), None)
        else Ok(success(Json.obj("orders" -> Json.toJson(orders))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list orders"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByCoinAddress",
    value = "Lists the orders associated with a given coin address",
    response = classOf[models.pay.api.Order],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid coin address"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No oreders found"),
    new ApiResponse(code = 500, message = "Error processing list orders by coin address request")))
  def listByCoinAddress(
    @ApiParam(
      name = "coinAddress",
      value = "The coin address of the orders to list",
      required = true)
    @PathParam("coinAddress")
    coinAddress: String,

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
  ) = Authorized("listByCoinAddress") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("coinAddress" -> coinAddress)
      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("accountId" -> token.account)
      }

      orderService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { orders =>
        if (orders.isEmpty) errors.toResult(CommonErrors.EmptyList("orders"), None)
        else Ok(success(Json.obj("orders" -> Json.toJson(orders))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list orders by coin address"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByType",
    value = "Lists the orders of a given type",
    response = classOf[models.pay.api.Order],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid order type"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No oreders found"),
    new ApiResponse(code = 500, message = "Error processing list orders by type request")))
  def listByType(
    @ApiParam(
      name = "orderType",
      value = "The order type",
      allowableValues = "payment,paymentRequest,refund",
      required = true)
    @PathParam("orderType")
    orderType: String,

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
  ) = Authorized("listByType") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("type" -> OrderType(orderType))
      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("accountId" -> token.account)
      }

      orderService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { orders =>
        if (orders.isEmpty) errors.toResult(CommonErrors.EmptyList("orders"), None)
        else Ok(success(Json.obj("orders" -> Json.toJson(orders))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list orders by type"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listByStatus",
    value = "Lists the orders in a given status",
    response = classOf[models.pay.api.Order],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid status"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No oreders found"),
    new ApiResponse(code = 500, message = "Error processing list orders by status request")))
  def listByStatus(
    @ApiParam(
      name = "status",
      value = "The status of the orders to list",
      allowableValues = "new,pending,processed,failed",
      required = true)
    @PathParam("status")
    status: String,

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
  ) = Authorized("listByStatus") { token =>
    Action.async { implicit request =>
      var selector = Json.obj("status" -> OrderStatus(status))
      if (!token.roles.contains(Role.Superuser.id)) {
        selector = selector ++ Json.obj("accountId" -> token.account)
      }

      orderService.find(
        selector,
        Some(Json.obj("$exclude" -> Json.arr("_version"))),
        Some(Json.obj("$desc" -> Json.arr("creationTime"))),
        page, perPage
      ).map { orders =>
        if (orders.isEmpty) errors.toResult(CommonErrors.EmptyList("orders"), None)
        else Ok(success(Json.obj("orders" -> Json.toJson(orders))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not list orders by status"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "POST",
    nickname = "listByRefId",
    value = "Lists the orders associated with an object in a specific domain",
    response = classOf[models.pay.api.Order],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid reference id data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "No orders found"),
    new ApiResponse(code = 422, message = "Reference id data with semantic errors"),
    new ApiResponse(code = 500, message = "Error processing list orders by reference id request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "refId",
    value = "The reference id data",
    required = true,
    dataType = "models.common.api.RefId",
    paramType = "body")))
  def listByRefId(
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
  ) = Authorized("listByRefId") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[RefId](refIdFormat).fold(
        valid = { refId =>
          var selector = Json.obj("refId.domain" -> refId.domain, "refId.name" -> refId.name, "refId.value" -> refId.value)
          if (!token.roles.contains(Role.Superuser.id)) {
            selector = selector ++ Json.obj("accountId" -> token.account)
          }

          orderService.find(
            selector,
            Some(Json.obj("$exclude" -> Json.arr("_version"))),
            Some(Json.obj("$desc" -> Json.arr("creationTime"))),
            page, perPage
          ).map { orders =>
            if (orders.isEmpty) errors.toResult(CommonErrors.EmptyList("orders"), None)
            else Ok(success(Json.obj("orders" -> Json.toJson(orders))))
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some("could not list orders by reference id"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing list orders by reference id request")))
        }
      )
    }
  }
}
