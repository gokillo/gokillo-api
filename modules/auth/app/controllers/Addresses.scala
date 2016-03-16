/*#
  * @file Addresses.scala
  * @begin 21-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.auth

import scala.concurrent.Future
import scala.util.control.NonFatal
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import services.common.{CommonErrors, DaoErrors}
import services.auth._
import utils.common.Formats._
import utils.common.Responses._
import utils.common.RequestHelper._
import models.common.Address
import models.common.Address._
import models.auth.{Token, User}

@Api(value = "/auth/users")
trait Addresses extends Controller with Security {

  protected val errors: CommonErrors with DaoErrors with AuthErrors
  protected val userService: UserDaoServiceComponent#UserDaoService
  protected val accountService: AccountDaoServiceComponent#AccountDaoService
  protected def Authorized(userId: String, operation: String)(action: Token => EssentialAction): EssentialAction

  @ApiOperation(
    httpMethod = "POST",
    nickname = "createAddress",
    value = "Creates a new address",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "invalid user id or invalid address data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 409, message = "Address name already used"),
    new ApiResponse(code = 422, message = "Address data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing create address request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "address",
    value = "The address data",
    required = true,
    dataType = "models.common.api.Address",
    paramType = "body")))
  def createAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to create the address for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "createAddress") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Address](addressFormat(Some(false))).fold(
        valid = { address =>
          userService.addAddress(userId, address).map {
            case Some(index) =>
              Logger.debug(s"created address $index for user $userId")
              Created(success).withHeaders(
                LOCATION -> s"$requestUri/$index"
              )
            case _ => errors.toResult(CommonErrors.NotFound("user",  userId), None)
          }.recover { case NonFatal(e) =>
            errors.toResult(e, Some(s"could not create address for user $userId"))
          }
        },
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing create address request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateAddress",
    value = "Updates an address",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 409, message = "Address name already used"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update address request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "address",
    value = "The update data",
    required = true,
    dataType = "models.common.api.Address",
    paramType = "body")))
  def updateAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to update the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the address",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "updateAddress") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Address](addressFormat(Some(true))).fold(
        valid = { update =>
          userService.updateAddress(userId, index, update).map {
          case Some(_) =>
            Logger.debug(s"updated address $index of user $userId")
            Ok(success)
          case _ => errors.toResult(CommonErrors.ElementNotFound("address", index.toString, "user", userId), None)
        }.recover { case NonFatal(e) =>
          errors.toResult(e, Some(s"could not update address $index of user $userId"))
        }},
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update address request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateAddressByName",
    value = "Updates an address by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 409, message = "Address name already used"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update address by name request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "address",
    value = "The update object",
    required = true,
    dataType = "models.common.api.Address",
    paramType = "body")))
  def updateAddressByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to update the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The address name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "updateAddressByName") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Address](addressFormat(Some(true))).fold(
        valid = { update =>
          userService.updateAddressByName(userId, name, update).map {
          case Some(_) =>
            Logger.debug(s"updated address named $name of user $userId")
            Ok(success)
          case _ => errors.toResult(CommonErrors.ElementNotFound("address", name, "user", userId), None)
        }.recover { case NonFatal(e) =>
          errors.toResult(e, Some(s"could not update address named $name of user $userId"))
        }},
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update address by name request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PUT",
    nickname = "updateDefaultAddress",
    value = "Updates the default address",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id or invalid update data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 409, message = "Address name already used"),
    new ApiResponse(code = 422, message = "Update data with semantic errors"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing update default address request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "address",
    value = "The update data",
    required = true,
    dataType = "models.common.api.Address",
    paramType = "body")))
  def updateDefaultAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to update the address for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "updateDefaultAddress") { token =>
    Action.async(parse.json) { implicit request =>
      request.body.validate[Address](addressFormat(Some(true))).fold(
        valid = { update =>
          userService.updateDefaultAddress(userId, update).map {
          case Some(_) =>
            Logger.debug(s"updated default address of user $userId")
            Ok(success)
          case _ => errors.toResult(CommonErrors.ElementNotFound("address", "default", "user", userId), None)
        }.recover { case NonFatal(e) =>
          errors.toResult(e, Some(s"could not update default address of user $userId"))
        }},
        invalid = { validationErrors =>
          val e = CommonErrors.InvalidRequest(requestUriWithMethod, Json.toJson(validationErrors))
          Future.successful(errors.toResult(e, Some("error parsing update default address request")))
        }
      )
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setDefaultAddress",
    value = "Sets an address as the default",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing set default address request")))
  def setDefaultAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to set the default address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the address",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "setDefaultAddress") { token =>
    Action.async { implicit request =>
      userService.setDefaultAddress(userId, index).map {
        case Some(_) =>
          Logger.debug(s"set address $index as the default address of user $userId")
          Ok(success)
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", index.toString, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set address $index as the default address of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "PATCH",
    nickname = "setDefaultAddressByName",
    value = "Sets an address as the default by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing set default address by name request")))
  def setDefaultAddressByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to set the default address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The address name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "setDefaultAddressByName") { token =>
    Action.async { implicit request =>
      userService.setDefaultAddressByName(userId, name).map {
        case Some(_) =>
          Logger.debug(s"set address named $name as the default address of user $userId")
          Ok(success)
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", name, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not set address named $name as the default address of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteAddress",
    value = "Deletes an address",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete address request")))
  def deleteAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the address",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "deleteAddress") { token =>
    Action.async { implicit request =>
      userService.removeAddress(userId, index).map {
        case Some(_) =>
          Logger.debug(s"deleted address $index of user $userId")
          Ok(success)
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", index.toString, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete address $index of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "DELETE",
    nickname = "deleteAddressByName",
    value = "Deletes an address by name",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 429, message = "Outdated data, try again"),
    new ApiResponse(code = 500, message = "Error processing delete address by name request")))
  def deleteAddressByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to delete the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The address name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "deleteAddressByName") { token =>
    Action.async { implicit request =>
      userService.removeAddressByName(userId, name).map {
        case Some(removedAddress) =>
          Logger.debug(s"deleted address named ${removedAddress.name} of user $userId")
          Ok(success)
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", name, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not delete address named $name of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findAddress",
    value = "Finds an address",
    response = classOf[models.common.api.Address])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 500, message = "Error processing find address request")))
  def findAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "index",
      value = "The zero-based index of the address",
      required = true)
    @PathParam("index")
    index: Int
  ) = Authorized(userId, "findAddress") { token =>
    Action.async { implicit request =>
      userService.findAddress(userId, index).map {
        case Some(address) => Ok(success(Json.obj("address" -> address.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", index.toString, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find address $index of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findAddressByName",
    value = "Finds an address by name",
    response = classOf[models.common.api.Address])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User or address not found"),
    new ApiResponse(code = 500, message = "Error processing find address by name request")))
  def findAddressByName(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the address for",
      required = true)
    @PathParam("userId")
    userId: String,

    @ApiParam(
      name = "name",
      value = "The address name",
      required = true)
    @PathParam("name")
    name: String
  ) = Authorized(userId, "findAddressByName") { token =>
    Action.async { implicit request =>
      userService.findAddressByName(userId, name).map {
        case Some(address) => Ok(success(Json.obj("address" -> address.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", name, "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find address named $name of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "findDefaultAddress",
    value = "Finds the default address",
    response = classOf[models.common.api.Address])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing find default address request")))
  def findDefaultAddress(
    @ApiParam(
      name = "userId",
      value = "The id of the user to find the address for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "findDefaultAddress") { token =>
    Action.async { implicit request =>
      userService.findDefaultAddress(userId).map {
        case Some(address) => Ok(success(Json.obj("address" -> address.asJson)))
        case _ => errors.toResult(CommonErrors.ElementNotFound("address", "default", "user", userId), None)
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not find default address of user $userId"))
      }
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "listAddresses",
    value = "Lists the addresses of a user",
    response = classOf[models.common.api.Address],
    responseContainer = "List")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid user id"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 500, message = "Error processing list addresses request")))
  def listAddresses(
    @ApiParam(
      name = "userId",
      value = "The id of the user to list the addresses for",
      required = true)
    @PathParam("userId")
    userId: String
  ) = Authorized(userId, "listAddresses") { token =>
    Action.async { implicit request =>
      userService.findAddresses(userId).map { addresses =>
        if (addresses.isEmpty) errors.toResult(CommonErrors.EmptyList("addresses", "user", userId), None)
        else Ok(success(Json.obj("addresses" -> Json.toJson(addresses))))
      }.recover { case NonFatal(e) =>
        errors.toResult(e, Some(s"could not list addresses of user $userId"))
      }
    }
  }
}
