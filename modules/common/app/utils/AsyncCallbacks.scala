/*#
  * @file TransactionCallbacks.scala
  * @begin 9-Sep-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scalaz.concurrent.Task
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Defines a set of callbacks to be invoked asynchronously.
  * @tparam T The type passed to the callbacks.
  */
class AsyncCallbacks[T] {

  private val tasks = new ListBuffer[Task[T => Future[Unit]]]()

  /** Gets the number of callbacks registered. */
  def count = tasks.length

  /** Clears all the registered callbacks. */
  def clear = tasks.clear

  /**
    * Adds the specified function to the list of callbacks to be invoked.
    * @param f  The function to add to the callback list.
    */
  def +=(f: T => Future[Unit]) = tasks += Task(f)

  /**
    * Invokes all the registered callbacks.
    *
    * @param data The data passed to the invoked callbacks.
    * @return     A `Future` containing the number of callbacks invoked.
    */
  def invoke(data: T) = Future {
    Task.gatherUnordered(tasks).map(_.map(_(data))).run.length
  }
}
