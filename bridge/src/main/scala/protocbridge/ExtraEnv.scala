package protocbridge

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat

/** Ad-hoc message-like class that represents extra environment available for protoc
  * code generators running through protocbridge.
  * This message gets appended by protocbridge to CodeGeneratorRequest so JVM plugins
  * get access to the environment.
  */
private final class ExtraEnv(val secondaryOutputDir: String) {
  def serializedSize: Int = {
    CodedOutputStream.computeStringSize(1, secondaryOutputDir)
  }

  def writeTo(cos: CodedOutputStream): Unit = {
    cos.writeString(1, secondaryOutputDir)
  }

  def toEnvMap: Map[String, String] = Map(
    ExtraEnv.ENV_SECONDARY_DIR -> secondaryOutputDir
  )

  private[protocbridge] def toByteArrayAsField: Array[Byte] = {
    val buf = new Array[Byte](
      serializedSize +
        CodedOutputStream.computeTagSize(
          ExtraEnv.EXTRA_ENV_FIELD_NUMBER
        ) + CodedOutputStream.computeUInt32SizeNoTag(serializedSize)
    )
    val cos = CodedOutputStream.newInstance(buf)
    cos.writeTag(1020, WireFormat.WIRETYPE_LENGTH_DELIMITED)
    cos.writeUInt32NoTag(serializedSize)
    writeTo(cos)
    cos.checkNoSpaceLeft()
    buf
  }
}

object ExtraEnv {
  val EXTRA_ENV_FIELD_NUMBER = 1020 // ScalaPB assigned extension number
  val ENV_SECONDARY_DIR = "SCALAPB_SECONDARY_OUTPUT_DIR"
}
