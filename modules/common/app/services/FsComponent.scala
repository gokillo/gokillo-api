/*#
  * @file FsComponent.scala
  * @begin 5-May-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package services.common

import scala.concurrent.Future
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import models.common.{Id, MetaFile, ByteRange}

/**
  * Defines functionality for storing and retrieving files.
  */
trait FsComponent {

  /**
    * Returns an instance of a `Fs` implementation.
    */
  def fs: Fs

  /**
    * Represents a file store object.
    */
  trait Fs {
    /**
      * Returns the number of files that match the specified criteria.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing the number of files
      *                 that match `selector`.
      */
    def count(selector: JsValue): Future[Int]

    /**
      * Saves the content provided by the specified `Enumerator` in the
      * file store.
      *
      * @param filename     The name of the file to store.
      * @param contentType  An `Option` containing the content type of the file to
      *                     store, or `None` if unspecified.
      * @param provider     The content provider.
      * @return             A `Future` value containing the meta-file.
      */
    def save(filename: String, contentType: Option[String], provider: Enumerator[Array[Byte]]): Future[MetaFile]

    /**
      * Updates the metadata of file identified by the specified id.
      *
      * @param id       The id that identifies the file to update the metadata for.
      * @param metadata The update metadata.
      * @return         A `Future` value containing `true` if the file metadata was
      *                 updated, or `false` if the file could not be found.
      */
    def update(id: Id, metadata: JsValue): Future[Boolean]

    /**
      * Updates the metadata of the files that match the specified criteria.
      *
      * @param selector The selector object.
      * @param metadata The update metadata.
      * @return         A `Future` value containing the number of files
      *                 affected by the update.
      */
    def update(selector: JsValue, metadata: JsValue): Future[Int]

    /**
      * Removes the specified file.
      *
      * @param file The file to remove.
      * @return     A `Future` value containing `true` if `file` was
      *             removed, or `false` if it could not be found.
      */
    def remove(file: MetaFile): Future[Boolean]

    /**
      * Removes the file identified by the specified id.
      *
      * @param id The id that identifies the file to remove.
      * @return   A `Future` value containing `true` if the file was
      *           removed, or `false` if it could not be found.
      */
    def remove(id: Id): Future[Boolean]

    /**
      * Removes the files that match the specified criteria.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing the number of
      *                 files removed.
      */
    def remove(selector: JsValue): Future[Int]

    /**
      * Gets an `Enumerator` of chunks of bytes matching the specified meta-file.
      *
      * @param file   The file to get the `Enumerator` for.
      * @param range  An `Option` value containing the range of bytes to retrieve,
      *               or `None` to retrieve the whole file.
      * @return       An `Enumerator` of chunks of bytes matching the specified meta-file.
      */
    def enumerate(file: MetaFile, range: Option[ByteRange]): Enumerator[Array[Byte]]

    /**
      * Gets an `Iteratee` that will consume data to be saved in the file store.
      *
      * @param filename     The name of the file to store.
      * @param contentType  An `Option` containing the content type of the file to
      *                     store, or `None` if unspecified.
      * @return             An `Iteratee` that will consume data to be saved in the
      *                     file store.
      */
    def iteratee(filename: String, contentType: Option[String]): Iteratee[Array[Byte], Future[MetaFile]]

    /**
      * Finds the file identified by the specified id.
      *
      * @param id The id that identifies the file to find.
      * @return   A `Future` value containing the meta-file identified
      *           by `id`, or `None` if it could not be found.
      */
    def find(id: Id): Future[Option[MetaFile]]

    /**
      * Finds the files that match the specified criteria, if any.
      *
      * @param selector The selector object.
      * @return         A `Future` value containing a `Seq` of meta-files
      *                 that match `selector`.
      */
    def find(selector: JsValue): Future[Seq[MetaFile]]
  } 
}
