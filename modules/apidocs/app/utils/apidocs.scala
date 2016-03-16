/*#
  * @file apidocs.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils

package object apidocs {

  import play.api.Configuration
  import com.wordnik.swagger.config._
  import com.wordnik.swagger.model._
  import com.wordnik.swagger.converter.{ModelConverters, OverrideConverter}
  import utils.common.env._
  import converters.apidocs._

  /**
    * Initializes Swagger.
    *
    * @param config The application configuration.
    * @note         `initSwagger` is invoked by `Global.onLoadConfig` just
    *               after configuration has been loaded but before the
    *               application actually starts.
    */
  def initSwagger(config: Configuration) = {
    loadConfig(config)
    loadModelConverters
    config
  }

  /**
    * Loads Swagger's configuration.
    * @param config The application configuration.
    */
  def loadConfig(config: Configuration) = {
    config.getString("application.name").foreach { appName =>
    config.getString("common.emails.apiTeam").foreach { contact =>
      val apiInfo = ApiInfo(
        title = s"$appName API",
        description = s"""
          Crowdfunding platform based on cryptocurrency that let people or organizations with great
          ideas publish their projects and get funded by other people who are willing to support them.
        """,
        termsOfServiceUrl = "",
        contact = contact,
        license = s"$appName API Subscription and Services Agreement",
        licenseUrl = peers(ApiDocs).endPoint("license")
      )

      ConfigFactory.config.info = Some(apiInfo)
    }}

    ConfigFactory.config.authorizations = List(
      ApiKey("Gok!llo", "header")
    )
  }

  /**
    * Loads custom model converters into Swagger.
    */
  def loadModelConverters = {
    ModelConverters.addConverter(new JodaLocalDateConverter, true)
    ModelConverters.addConverter(new PasswordConverter, true)
  }
}
