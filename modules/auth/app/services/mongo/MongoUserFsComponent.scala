/*#
  * @file MongoUserFsComponent.scala
  * @begin 19-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.auth.mongo

import services.common.mongo.MongoFsComponent

/**
  * Implements the user file store component for Mongo.
  */
trait MongoUserFsComponent extends MongoFsComponent {

  def namespace = "users"

  fieldMaps = Map(
    "metadata.userId" -> ("metadata.userId", Some("$oid"))
  )
}
