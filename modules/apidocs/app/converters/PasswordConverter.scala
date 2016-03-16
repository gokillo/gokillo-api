/*#
  * @file PasswordConverter.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package converters.apidocs

import com.wordnik.swagger.converter.OverrideConverter

/**
  * Provides functionality for converting `Password` fields.
  */
class PasswordConverter extends OverrideConverter {

  add("brix.security.Password",
    """{
      "id": "Password",
      "properties": {
        "value": {
          "required": true,
          "description": "The password in plaintext",
          "type": "string",
          "format": "string"
        },
        "salt": {
          "required": false,
          "description": "The salt used to hash the password",
          "notes": "Generated automatically if not specified",
          "type": "string",
          "format": "string"
        }
      }
    }"""
  )
}
