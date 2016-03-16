/*#
  * @file ErrorFilter.scala
  * @begin 25-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package filters

import scala.util.control.NonFatal
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialFilter, EssentialAction, RequestHeader}

/**
  * Implements the error fallback filter.
  */
object ErrorFilter extends EssentialFilter {

  import services.common.ErrorStackBase
  import services.common.ErrorStackBase._

  private val errors = new ErrorStackBase {}

  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(request: RequestHeader) = {
      nextFilter(request) recoverWith {
        case NonFatal(e) => 
          Iteratee.ignore[Array[Byte]].map(_ => errors.toResult(e, Some("internal service error")))
      }
    }
  }
}
