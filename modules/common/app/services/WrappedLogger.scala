/*#
  * @file WrappedLogger.scala
  * @begin 29-Jun-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import play.api.libs.json.Json
import play.api.{Logger => logger}

/**
  * Extends `Logger` with additional functionality to print possible error
  * details as pretty JSON.
  *
  * @see [[https://www.playframework.com/documentation/2.4.x/api/scala/index.html#play.api.Logger$]]
  */
object WrappedLogger {

  def isTraceEnabled = logger.isTraceEnabled
  def isDebugEnabled = logger.isDebugEnabled
  def isInfoEnabled = logger.isInfoEnabled
  def isWarnEnabled = logger.isWarnEnabled
  def isErrorEnabled = logger.isErrorEnabled

  def trace(message: => String) = logger.trace(message)
  def trace(message: => String, error: => Throwable) {
    if (logger.isTraceEnabled) {
      logger.trace(message, error)
      if (error.isInstanceOf[BaseException]) error.asInstanceOf[BaseException].details foreach { details =>
        logger.trace(Json.prettyPrint(details))
      }
    }
  }

  def debug(message: => String) = logger.debug(message)
  def debug(message: => String, error: => Throwable) {
    if (logger.isDebugEnabled) {
      logger.debug(message, error)
      if (error.isInstanceOf[BaseException]) error.asInstanceOf[BaseException].details foreach { details =>
        logger.debug(Json.prettyPrint(details))
      }
    }
  }

  def info(message: => String) = logger.info(message)
  def info(message: => String, error: => Throwable) {
    if (logger.isInfoEnabled) {
      logger.info(message, error)
      if (error.isInstanceOf[BaseException]) error.asInstanceOf[BaseException].details foreach { details =>
        logger.info(Json.prettyPrint(details))
      }
    }
  }

  def warn(message: => String) = logger.warn(message)
  def warn(message: => String, error: => Throwable) {
    if (logger.isWarnEnabled) {
      logger.warn(message, error)
      if (error.isInstanceOf[BaseException]) error.asInstanceOf[BaseException].details foreach { details =>
        logger.warn(Json.prettyPrint(details))
      }
    }
  }

  def error(message: => String) = logger.error(message)
  def error(message: => String, error: => Throwable) {
    if (logger.isErrorEnabled) {
      logger.error(message, error)
      if (error.isInstanceOf[BaseException]) error.asInstanceOf[BaseException].details foreach { details =>
        logger.error(Json.prettyPrint(details))
      }
    }
  }
}
