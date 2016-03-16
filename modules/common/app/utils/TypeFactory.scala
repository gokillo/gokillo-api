/*#
  * @file TypeFactory.scala
  * @begin 12-Feb-2014
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2014 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import scala.reflect.api._
import scala.reflect.runtime._
import scala.reflect.runtime.universe._

/**
  * Provides functionality for initializing new instances of the
  * specified type.
  *
  * @tparam T The type to initialize new instances of.
  */
abstract class TypeFactory[T <: AnyRef: TypeTag] {

  /**
    * Initializes a new instance of `T` with the specified arguments.
    *
    * @param args The arguments taken by `ctor`.
    * @param ctor A zero-based index that identifies the constructor
    *             to be invoked - default to 0.
    * @return     A new instancce of `T`.
    */
  def newInstance(args: AnyRef*)(ctor: Int = 0): T = {
    val tt = typeTag[T]
    currentMirror.reflectClass(tt.tpe.typeSymbol.asClass).reflectConstructor(
      tt.tpe.members.filter(m =>
        m.isMethod && m.asMethod.isConstructor
      ).iterator.toSeq(ctor).asMethod
    )(args: _*).asInstanceOf[T]
  }
}
