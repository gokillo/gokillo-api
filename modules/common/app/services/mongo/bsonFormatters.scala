/*#
  * @file bsonFormatters.scala
  * @begin 11-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common.mongo

import reactivemongo.bson._

/**
  * Provides a set of BSON formatters. BSON formatters include both `BSONReader`
  * and `BSONWriter` implementations.
  */
package object bsonFormatters {

  /**
    * Implements a reader that produces `String` instances from
    * BSON values.
    */
  implicit object BSONValueStringReader extends BSONReader[BSONValue, String] {
    
    /**
      * Reads a BSON value and converts it into a `String`.
      *
      * @param bson The BSON value to convert.
      * @return     The `String` converted from `bson`.
      */
    def read(bson: BSONValue) = bson match { 
      case oid: BSONObjectID => oid.stringify 
    }
  }
}
