/*#
  * @file Jwt.scala
  * @begin 10-Jul-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package models.auth.api

import com.wordnik.swagger.annotations._

/**
  * Defines the `Jwt` schema for Swagger.
  */
@ApiModel(value = "Jwt", description = "Represents a JSON Web Token")
trait Jwt {}
