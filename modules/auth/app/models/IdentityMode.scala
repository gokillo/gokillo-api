/*#
  * @file IdentityMode.scala
  * @begin 8-Dec-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import play.api.libs.json._
import play.api.data.validation.ValidationError
import services.common.CommonErrors._
import utils.common.typeExtensions._

/**
  * Defines identity types applied to originators or backers.
  */
object IdentityMode extends Enumeration {

  type IdentityMode = Value

  /**
    * The identity is anonymous.
    */
  val Anonym = Value("anonym")

  /**
    * The identity is given by username.
    */
  val Username = Value("username")

  /**
    * The identity is given by first and last name.
    */
  val FullName = Value("fullName")

  /**
    * The identity is given by company name.
    */
  val CompanyName = Value("companyName")

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): IdentityMode = {
    try {
      IdentityMode.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("identity type", name)
    }
  }

  /**
    * Serializes/Deserializes an [[IdentityMode]] to/from JSON.
    */
  implicit val identityModeFormat = new Format[IdentityMode] {
    def reads(json: JsValue) = JsSuccess(IdentityMode(json.as[JsString].value))
    def writes(identityMode: IdentityMode) = JsString(identityMode.toString)
  }

  /*
   * Validates the current identity type.
   */
  def identityMode(implicit reads: Reads[String]) = Reads[String]( js =>
    reads.reads(js).flatMap { v =>
      if (IdentityMode.values.map(_.toString).contains(v)) JsSuccess(v)
      else JsError(ValidationError("error.identityMode", IdentityMode.values.mkString("|")))
    }
  )
}
