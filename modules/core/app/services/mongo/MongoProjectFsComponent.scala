/*#
  * @file MongoProjectFsComponent.scala
  * @begin 19-Aug-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import services.common.mongo.MongoFsComponent

/**
  * Implements the project file store component for Mongo.
  */
trait MongoProjectFsComponent extends MongoFsComponent {

  def namespace = "projects"

  fieldMaps = Map(
    "metadata.projectId" -> ("metadata.projectId", Some("$oid")),
    "metadata.rewardId" -> ("metadata.rewardId", Some("$oid")),
    "metadata.state.timestamp" -> ("metadata.state.timestamp", Some("$date"))
  )
}
