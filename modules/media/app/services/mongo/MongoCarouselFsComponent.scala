/*#
  * @file MongoCarouselFsComponent.scala
  * @begin 10-May-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package services.media.mongo

import services.common.mongo.MongoFsComponent

/**
  * Implements the carousel file store component for Mongo.
  */
trait MongoCarouselFsComponent extends MongoFsComponent {

  def namespace = "carousel"
}
