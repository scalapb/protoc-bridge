package protocbridge

import scala.collection.mutable.ArrayBuilder
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream

object ProtoUtils {
  def writeRawVarint32(output: ArrayBuilder[Byte], value0: Int): Unit = {
    var value = value0
    while (true) {
      if ((value & ~0x7f) == 0) {
        output += value.toByte
        return
      } else {
        output += ((value & 0x7f) | 0x80).toByte
        value >>>= 7
      }
    }
  }

  def computeRawVarint32Size(value: Int): Int = {
    if ((value & (0xffffffff << 7)) == 0) return 1
    if ((value & (0xffffffff << 14)) == 0) return 2
    if ((value & (0xffffffff << 21)) == 0) return 3
    if ((value & (0xffffffff << 28)) == 0) return 4
    5
  }

  def writeTag(b: ArrayBuilder[Byte], fieldNumber: Int, wireType: Int): Unit = {
    writeRawVarint32(b, makeTag(fieldNumber, wireType))
  }

  def writeString(
      b: ArrayBuilder[Byte],
      fieldNumber: Int,
      value: String
  ): Unit = {
    writeTag(b, fieldNumber, WIRETYPE_LENGTH_DELIMITED)
    writeStringNoTag(b, value)
  }

  def writeBytes(
      b: ArrayBuilder[Byte],
      fieldNumber: Int,
      value: Array[Byte]
  ): Unit = {
    writeTag(b, fieldNumber, WIRETYPE_LENGTH_DELIMITED)
    writeBytesNoTag(b, value)
  }

  def writeBytesNoTag(b: ArrayBuilder[Byte], value: Array[Byte]) = {
    writeRawVarint32(b, value.length)
    b ++= value
  }

  def writeStringNoTag(b: ArrayBuilder[Byte], value: String): Unit = {
    val bytes = value.getBytes(UTF_8)
    writeBytesNoTag(b, bytes)
  }

  def computeTagSize(fieldNumber: Int): Int =
    computeRawVarint32Size(makeTag(fieldNumber, 0))

  def computeStringSize(fieldNumber: Int, s: String): Int = {
    val sz = s.getBytes(UTF_8).length
    computeTagSize(fieldNumber) + computeRawVarint32Size(sz) + sz
  }

  val WIRETYPE_LENGTH_DELIMITED = 2
  val TAG_TYPE_BITS = 3

  def makeTag(fieldNumber: Int, wireType: Int) =
    (fieldNumber << TAG_TYPE_BITS) | wireType

  val UTF_8 = java.nio.charset.Charset.forName("UTF-8")
}
