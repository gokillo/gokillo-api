/*#
  * @file Global.scala
  * @begin 7-Dec-2013
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2013 <a href="http://gokillo.com">Gokillo</a>
  */

import play.api._
import play.api.Play.current
import play.api.mvc.WithFilters
import java.io.File
import utils.common._
import utils.common.env._
import utils.apidocs._
import filters._
import CorsStrategy._

/**
  * Defines custom global settings.
  */
object Global extends WithFilters(ErrorFilter) with CorsSupport {

  /**
    * Called just after configuration has been loaded.
    */
  override def onLoadConfig(
    config: Configuration,
    path: File,
    classLoader: ClassLoader,
    mode: Mode.Mode
  ): Configuration = {
    config.getString("cors.allowedOrigins").foreach { _ match {
      case allowedOrigins if allowedOrigins == "*" => corsStrategy = Origin
      case allowedOrigins => corsStrategy = WhiteList(allowedOrigins.replaceAll(" ", "").split(","): _*)
    }}

    initEnv(config)
    initSwagger(config)
  }

  /**
    * Called once the application is started.
    */
  override def onStart(app: Application) = {
    if (!Play.isDev && localhost.endPoint.hostName == EndPoint.DefaultHostName) {
      Logger.error(s"current application mode is ${Play.mode} but public host is ${localhost.endPoint}")
      play.api.Play.stop
      System.exit(1)
    }
  }
}
