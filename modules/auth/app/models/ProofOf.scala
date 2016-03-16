/*#
  * @file ProofOf.scala
  * @begin 13-Mar-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

import play.api.libs.json._
import utils.common.typeExtensions._
import services.common.CommonErrors._

/**
  * Defines proof ofs.
  */
object ProofOf extends Enumeration {

  type ProofOf = Value

  /**
    * Defines a proof of identity.
    */
  val Identity = Value("identity")

  /**
    * Defines a proof of address.
    */
  val Address = Value("address")

  /**
    * Defines a proof of incorporation.
    */
  val Incorporation = Value("incorporation")

  /**
    * Serializes/Deserializes a [[ProofOf]] to/from JSON.
    */
  implicit val ProofOfFormat = new Format[ProofOf] {
    def reads(json: JsValue) = JsSuccess(ProofOf(json.as[JsString].value))
    def writes(proofOf: ProofOf) = JsString(proofOf.toString)
  }

  /**
    * Returns the value with the specified name.
    *
    * @param name The name of the value.
    * @return     The value whose name is `name`.
    */
  def apply(name: String): ProofOf = {
    try {
      return ProofOf.withName(name.uncapitalize)
    } catch { case e: NoSuchElementException =>
      throw NotSupported("proof", name)
    }
  }
}
