/*#
  * @file ReflectHelperr.scala
  * @begin 25-Mar-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.reflect.runtime.universe._

/**
  * Provides functionality for obtaining reflective information about
  * classes and objects.
  */
object ReflectHelper {

  /**
    * Returns a `Map` from annotation names to annotation data for
    * the specified type.
    *
    * @tparam T The type to get class annotations for.
    * @return   The class annotations for `T`.
    */
  def classAnnotations[T: TypeTag]: Map[String, Map[String, Any]] = {
    typeOf[T].typeSymbol.asClass.annotations.map { a =>
      a.tree.tpe.typeSymbol.name.toString -> a.tree.children.withFilter {
        _.productPrefix eq "AssignOrNamedArg"
      }.map { tree =>
        tree.productElement(0).toString -> tree.productElement(1)
      }.toMap
    }.toMap
  }

  /**
    * Returns a `Map` from method names to a `Map` from annotation names to
    * annotation data for the specified type.
    *
    * @tparam T The type to get method annotations for.
    * @return   The method annotations for `T`.
    */
  def methodAnnotations[T: TypeTag]: Map[String, Map[String, Map[String, Any]]] = {
    typeOf[T].decls.collect { case m: MethodSymbol => m }.withFilter {
      _.annotations.length > 0
    }.map { m =>
      m.name.toString -> m.annotations.map { a =>
        a.tree.tpe.typeSymbol.name.toString -> a.tree.children.withFilter {
         _.productPrefix eq "AssignOrNamedArg"
        }.map { tree =>
          tree.productElement(0).toString -> tree.productElement(1)
        }.toMap
      }.toMap
    }.toMap
  }
}
