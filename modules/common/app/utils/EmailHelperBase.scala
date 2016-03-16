/*#
  * @file EmailHelperBase.scala
  * @begin 16-Dec-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import play.api.i18n.Lang
import services.common.{EmailServiceComponent, DefaultEmailServiceComponent, DefaultEmailComponent}

/**
  * Provides base functionality for email helpers.
  */
trait EmailHelperBase {

  /** The component that implements the mailing service. */
  protected val emailService: EmailServiceComponent#EmailService = new DefaultEmailServiceComponent
    with DefaultEmailComponent {
  }.emailService

  /**
    * Returns a `Lang` instance for the specified ISO language code.
    *
    * @param code The ISO language code to get the `Lang` instance for.
    * @return     A new `Lang` instance for `code`.
    * @note       Valid language codes consist of an ISO 639-2 language code,
    *             optionally followed by an ISO 3166-1 alpha-2 country code.
    */
  def lang(code: Option[String] = None): Lang = {
    code match {
      case Some(code) => Lang(code)
      case _ => Lang.defaultLang
    }
  }
}
