/*#
  * @file JodaLocalDateConverter.scala
  * @begin 25-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package converters.apidocs

import com.wordnik.swagger.model.Model
import com.wordnik.swagger.converter.{ModelConverter, BaseConverter}

/**
  * Provides functionality for converting `LocalDate` fields.
  */
class JodaLocalDateConverter extends ModelConverter with BaseConverter {

  def read(cls: Class[_], typeMap: Map[String, String]): Option[Model] = None

  override def typeMap = Map("localdate" -> "date")

  override def ignoredClasses: Set[String] = Set("org.joda.time.LocalDate")
}
