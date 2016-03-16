/*#
  * @file QrCode.scala
  * @begin 11-Apr-2015
  * @author <a href="mailto:giuseppe.greco@gokillo.com">Giuseppe Greco</a>
  * @copyright 2015 <a href="http://gokillo.com">Gokillo</a>
  */

package utils.common

import java.util.{HashMap => JavaHashMap}
import java.awt.{Color, Graphics2D}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.apache.commons.codec.binary.Base64
import com.google.zxing.{BarcodeFormat, EncodeHintType}
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
  * Represents a matrix barcode.
  *
  * @constructor  Initializes a new instance of the [[QrCode]] class.
  * @param text   The text to encode.
  * @param size   The side size of the matrix barcode.
  * @param color1 The RGB value of the first color.
  * @param color2 The RGB value of the second color.
  * @param format The format of the matrix barcode.
  */
class QrCode private(
  val text: String,
  val size: Int,
  val color1: Int,
  val color2: Int,
  val format: String
) {

  private val Color1 = new Color(color1)
  private val Color2 = new Color(color2)
  private val hints = new JavaHashMap[EncodeHintType, Any]() {
    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
  }

  private var matrix: BufferedImage = _; encode

  /**
    * Encodes the matrix barcode.
    */
  private def encode = {
    val bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val width = bitMatrix.getWidth

    matrix = new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB);
    matrix.createGraphics

    val graphics = matrix.getGraphics.asInstanceOf[Graphics2D]
    graphics.setColor(Color1)
    graphics.fillRect(0, 0, width, width);
    graphics.setColor(Color2);

    for (i <- 0 to width - 1) {
      for (j <- 0 to width - 1) {
        if (bitMatrix.get(i, j)) graphics.fillRect(i, j, 1, 1)
      }
    }
  }

  /**
    * Converts this `QrCode` to a byte array.
    */
  def toByteArray: Array[Byte] = {
    val outputStream = new ByteArrayOutputStream
    ImageIO.write(matrix, format, outputStream)
    outputStream.toByteArray
  }

  /**
    * Converts this `QrCode` to a Base64 string.
    */
  def toBase64String: String = Base64.encodeBase64String(toByteArray)
}

/**
  * Factory class for creating [[QrCode]] instances.
  */
object QrCode {

  /**
    * Initializes a new instance of the [[QrCode]] class with the specifed values.
    *
    * @param text   The text to encode.
    * @param size   The side size of the matrix barcode.
    * @param color1 The RGB value of the first color.
    * @param color2 The RGB value of the second color.
    * @param format The format of the matrix barcode, default to ''png''.
    * @return       A new instance of the [[QrCode]] class.
    */
  def apply(
    text: String,
    size: Int,
    color1: Int = 0xFFFFFF,
    color2: Int = 0x000000,
    format: String = "png"
  ) = new QrCode(text, size, color1, color2, format)


  /**
    * Implicitly converts the specified [[QrCode]] to a byte array.
    */
  implicit def toByteArray(qrCode: QrCode) = qrCode.toByteArray

  /**
    * Implicitly converts the specified [[QrCode]] to a Base64 string.
    */
  implicit def toBase64String(qrCode: QrCode) = qrCode.toBase64String
}
