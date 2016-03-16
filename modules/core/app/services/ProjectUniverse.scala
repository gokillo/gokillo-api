/*#
  * @file ProjectUniverse.scala
  * @begin 17-Jun-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.core

import services.common.{FsServiceComponent, DefaultFsServiceComponent}
import mongo._

/**
  * Provides shared data and functionality for dealing with projects.
  */
object ProjectUniverse {

  /**
    * The DAO service that provides access to project wip.a
    * @note '''wip''' stands for '''w'''ork '''i'''n '''p'''rogress.
    */
  implicit val wipService: ProjectDaoServiceComponent#ProjectDaoService = new ProjectDaoServiceComponent
    with MongoProjectDaoComponent {
      def wip = true
  }.daoService.asInstanceOf[ProjectDaoServiceComponent#ProjectDaoService]

  /**
    * The DAO service that provides access to project data.
    */
  implicit val daoService: ProjectDaoServiceComponent#ProjectDaoService = new ProjectDaoServiceComponent
    with MongoProjectDaoComponent {
      def wip = false
  }.daoService.asInstanceOf[ProjectDaoServiceComponent#ProjectDaoService]

  /**
    * The FS service that provides access to project files.
    */
  implicit val fsService: FsServiceComponent#FsService = new DefaultFsServiceComponent
    with MongoProjectFsComponent {
  }.fsService
}
