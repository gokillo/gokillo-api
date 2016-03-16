/*#
  * @file MongoAlgorithmFsComponent.scala
  * @begin 1-Jan-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core.mongo

import services.common.mongo.MongoFsComponent

/**
  * Implements the machine learning algorithm file store component for Mongo.
  */
trait MongoAlgorithmFsComponent extends MongoFsComponent {

  def namespace = "algorithms"
}
