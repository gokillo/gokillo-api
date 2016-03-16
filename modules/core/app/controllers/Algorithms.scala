/*#
  * @file Algorithms.scala
  * @begin 1-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package controllers.core

import scala.concurrent.Future
import scala.util.{Success, Failure}
import scala.util.control.NonFatal
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import javax.ws.rs.PathParam
import com.wordnik.swagger.annotations._
import play.api._
import play.api.mvc.{Security => _, _}
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.common.FsController
import controllers.auth.Security
import services.common.{CommonErrors, DaoErrors, FsServiceComponent, DefaultFsServiceComponent}
import services.auth.AuthErrors
import services.core.CoreErrors
import services.core.mongo.MongoAlgorithmFsComponent
import services.core.machineLearning._
import utils.common.{parse => parseNumber}
import utils.common.Responses._

@Api(
  value = "/core/algorithms",
  description = "Train machine learning algorithms",
  authorizations = Array(new Authorization(value = Security.AuthScheme)))
object Algorithms extends Controller with Security with FsController {

  private final val FundingModelFile = "funding-model.dat"

  private implicit val fsService: FsServiceComponent#FsService = new DefaultFsServiceComponent
    with MongoAlgorithmFsComponent {
  }.fsService

  protected val errors = new CommonErrors with DaoErrors with AuthErrors with CoreErrors {}

  @ApiOperation(
    httpMethod = "POST",
    nickname = "trainFundingModel",
    value = "Trains the funding model",
    notes = "Input CSV file must define the following fields: range, fundraising duration, target amount, raised amount, and category (0 or 1)",
    response = classOf[Void])
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid train data"),
    new ApiResponse(code = 401, message = "Request not authorized"),
    new ApiResponse(code = 403, message = "Authorization token violated or expired"),
    new ApiResponse(code = 500, message = "Error processing train funding model request")))
  @ApiImplicitParams(Array(new ApiImplicitParam(
    name = "trainData",
    value = "The data to be used to train the funding model",
    required = true,
    dataType = "file",
    paramType = "body")))
  def trainFundingModel = Authorized("trainFundingModel") { token =>
    Action.async(parse.raw) { implicit request =>
      def isValid(line: String) = """^\d+-\d+,\d+,\d+,\d+,[0|1]$""".r.unapplySeq(line).isDefined
      var currRange: String = null; var prevRange: String = null
      var scaling: Int = 1
      val lines = ArrayBuffer[Array[String]]()
      val trainData = ArrayBuffer[Observation]()
      var ranges = ArrayBuffer[String]()

      request.body.asBytes(request.body.size.toInt).foreach { bytes =>
        Source.fromBytes(bytes).getLines.foreach { line => if (isValid(line)) {
          lines += line.replaceAll("\\s", "").split(",")
          currRange = lines.last(0)

          if (prevRange != currRange) {
            ranges += currRange
            prevRange = currRange
          }
        }}

        // sort by min value in range
        ranges = ranges.sortBy(_.split("-")(0).toInt).distinct

        // calculate feature scaling (multiple of 10, starting from 1)
        scaling = Math.abs(("1" + "0" * ranges.map(
          _.split("-")(0) // get min value in range
        ).filter(
          _ != "0"        // skip if min value in range is 0
        ).map(
        _.length - 2      // decrement value length by 2 digits
        ).min).toInt)

        lines.foreach { fields =>
          trainData += FundraisingObservation(
            fields(0),                          // range
            parseNumber[Double](fields(1)).get, // fundraising duration
            parseNumber[Double](fields(2)).get, // target amount
            parseNumber[Double](fields(3)).get, // raised amount
            parseNumber[Int](fields(4)).get,    // category (0 or 1)
            scaling                             // feature scaling
          )
        }
      }

      if (trainData.nonEmpty) {
        Future(LogisticRegression.train(trainData.toList).serialize).flatMap {
          case Success(fundingModel) =>
            fsService.save(
              FundingModelFile,
              Some("application/octet-stream"),
              Enumerator(fundingModel)
            ).flatMap { file =>
              fsService.update(
                file.id,
                Json.obj("metadata" -> Json.obj(
                  "classes" -> Json.toJson(ranges),
                  "scaling" -> scaling,
                  "category" -> "algorithm"
                ))
              ).flatMap { _ =>
                fsService.remove(Json.obj(
                  "filename" -> FundingModelFile,
                  "$lt" -> Json.obj("uploadDate" -> file.uploadDateMillis)
                ))
              }.map { _ =>
                Logger.info("funding model trained successfully")
                Ok(success)
              }
            }
          case Failure(e) => Future.failed(e)
        }.recover { case NonFatal(e) =>
          errors.toResult(e, Some("could not train funding model"))
        }
      } else Future.successful(errors.toResult(
        CoreErrors.InvalidTrainData(
          "range",
          "fundraising duration",
          "target amount",
          "raised amount",
          "category (0 or 1)"
        ), Some("could not train funding model")
      ))
    }
  }

  @ApiOperation(
    httpMethod = "GET",
    nickname = "getFundingModel",
    value = "Gets the trained funding model",
    response = classOf[Array[Byte]])
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "Funding model not trained yet"),
    new ApiResponse(code = 416, message = "Requested range is invalid or not available"),
    new ApiResponse(code = 500, message = "Error processing get funding model request")))
  def getFundingModel = Authorized("getFundingModel") { token =>
    Action.async { implicit request =>
      fsService.find(
        Json.obj("filename" -> FundingModelFile)
      ).flatMap { _.headOption match {
        case Some(file) => serveFile(file)
        case _ => Future.successful(errors.toResult(CommonErrors.NotFound("file", FundingModelFile), None))
      }}.recover { case NonFatal(e) =>
        errors.toResult(e, Some("could not get funding model"))
      }
    }
  }
}
