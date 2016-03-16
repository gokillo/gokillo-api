/*#
  * @file ByteRange.scala
  * @begin 11-Feb-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package models.common

/**
  * Represents a range of bytes.
  *
  * @constructor  Initializes a new instance of the [[ByteRange]] class.
  * @param first  The position of the first byte.
  * @param last   The position of the last byte.
  */
class ByteRange private(val first: Int, val last: Int) {

  /**
    * Gets the length of the byte range.
    */
  lazy val length = last - first + 1

  override def toString = s"$first-$last"
}

/**
  * Factory class for creating [[ByteRange]] instances.
  */
object ByteRange {

  /**
    * Initializes a new instance of the [[ByteRange]] class.
    *
    * @param first  The position of the first byte, 0-based.
    * @param last   The position of the last byte, 0-based.
    * @return       A new instance of the [[ByteRange]] class.
    */
  def apply(first: Int, last: Int): ByteRange = {
    if (first < 0) throw new IllegalArgumentException("first is less than zero")
    if (last < 0) throw new IllegalArgumentException("last is less than zero")
    if (last < first) throw new IllegalArgumentException("last is less than first")

    new ByteRange(first, last)
  }

  /**
    * Parses the specified range string and initializes a new instance of
    * the [[ByteRange]] class.
    *
    * @param range        The range string to parse.
    * @param defaultLast  The default position of the last byte if not provided in `range`.
    * @param defaultFirst The default position of the first byte if not provided in `range`.
    * @return             A new instance of the [[ByteRange]] class.
    */
  def apply(range: String, defaultLast: Int, defaultFirst: Int = 0): ByteRange = {
    import utils.common.parse

    val t = """^([0-9]*)\-([0-9]*)$""".r.findFirstMatchIn(range) match {
      case Some(v) => (v.group(1), v.group(2))
      case _ => ("", "")
    }

    ByteRange(
      parse[Int](t._1).getOrElse(defaultFirst),
      parse[Int](t._2).getOrElse(defaultLast)
    )
  }
}
