/*#
  * @file DaoErrors.scala
  * @begin 19-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

trait DaoErrors extends ErrorStackBase {

  /** Converts dao errors to HTTP `Result`. */
  override def toResult = DaoErrors.resolver orElse super.toResult
}

object DaoErrors {

  import play.api.mvc.Results._
  import utils.common.typeExtensions._
  import ErrorStackBase._

  final case class DatabaseError(collection: String, cause: Exception) extends BaseException(
    s"database error on collection $collection: ${cause.getMessage}"
  )
  final case class DuplicateKey(field: String, collection: String) extends BaseException(
    s"duplicate key on field $field in collection $collection"
  )
  final case class InvalidId(id: String, field: String) extends BaseException(
    s"$id is an invalid object id and cannot be assigned to field $field"
  )
  final case class MissingField(field: String, collection: String) extends BaseException(
    s"field $field is missing but is required to access collection $collection"
  )
  final case class NoData(collection: String, dataType: String = "data") extends BaseException(
    s"no $dataType to insert or update in collection $collection"
  )
  final case class StaleObject(objectId: String, collection: String) extends BaseException(
    s"object $objectId in collection $collection was outdated and could not be updated or deleted: try again"
  )

  /**
    * Gets a `Resolver` that converts dao errors to HTTP `Result`.
    * @note Errors get logged with the appropriate log level.
    */
  val resolver: Resolver = {
    case (e@(_: DuplicateKey), log) => log.foreach(WrappedLogger.debug(_, e)); Conflict(e.toJson())
    case (e@(
      _: InvalidId |
      _: MissingField
    ), log) => log.foreach(WrappedLogger.debug(_, e)); BadRequest(e.toJson())
    case (e@(_: StaleObject), log) => log.foreach(WrappedLogger.debug(_, e)); TooManyRequest(e.toJson())
  }
}
