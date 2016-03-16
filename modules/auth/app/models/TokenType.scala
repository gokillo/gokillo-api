/*#
  * @file TokenType.scala
  * @begin 15-Apr-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth

/**
  * Defines token types.
  */
object TokenType extends Enumeration {

  type TokenType = Value

  /**
    * Indentifies a `Token` generated for an API consumer that grants
    * subjects access to secured actions with browsing privileges.
    */
  val Browse = Value("BROWSE")

  /**
    * Identifies a `Token` that lets subject's account be activated.
    */
  val Activation = Value("ACTV")

  /**
    * Identifies a `Token` that provides access to secured actions
    * according to subject's claims.
    */
  val Authorization = Value("AUTH")

  /**
    * Identifies a `Token` that enables reset processes.
    */
  val Reset = Value("RESET")

  /**
    * Gets all the defined `Token` types.
    */
  val `*` = Seq(Browse, Activation, Authorization, Reset)
}
