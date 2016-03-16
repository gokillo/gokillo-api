/*#
  * @file WithRetry.scala
  * @begin 6-Oct-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object WithRetry {

  /**
    * Executes the specified function until it does not return `Some`,
    * up to the specified amount of time.
    *
    * @param duration The amount of time, in milliseconds, retries last.
    * @param f        The function to execute.
    * @param progress The amount of time, in milliseconds, to wait for the next call.
    * @return         An `Option` value containing the result of `f`, or `None` if the
    *                 result could not be returned within the specified amount of time.
    */
  def apply[T](duration: Int)(f: => Future[Option[T]])(implicit progress: Int = 1000): Future[Option[T]] = {
    f.flatMap {
      case Some(t) => f
      case None => duration - progress match {
        case remaining if remaining > 0 => Thread.sleep(progress); apply(remaining)(f)
        case _ => f
      }
    }
  }

  /**
    * Executes the specified function until the specified condition does not match.
    *
    * @param cond A `T => Boolean` function that evaluates the exit condition.
    * @param f    The function to execute.
    * @return     The return value of `f`.
    */
  def apply[T](cond: T => Boolean)(f: => Future[T]): Future[T] = {
    f.flatMap {
      case t => cond(t) match {
        case false => apply(cond)(f)
        case _ => f
      }
    }
  }
}
