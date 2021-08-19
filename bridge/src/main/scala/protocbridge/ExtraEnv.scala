package protocbridge

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.TextFormat
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest

/** Ad-hoc message-like class that represents extra environment available for
  * protoc code generators running through protocbridge. This message gets
  * appended by protocbridge to CodeGeneratorRequest so JVM plugins get access
  * to the environment. We do not generate Java or Scala code for this message
  * to prevent potential binary compatibility issues between the bridge and the
  * plugin. Instead, we serialize directly to bytes. Parsing is done in a
  * sandboxed environment, so we rely on DynamicMessage then.
  */
final class ExtraEnv(val secondaryOutputDir: String) {
  def toEnvMap: Map[String, String] = Map(
    ExtraEnv.ENV_SECONDARY_DIR -> secondaryOutputDir
  )

  // Serializes this message as a field. Used to append it to a CodeGeneratorRequest
  private[protocbridge] def toByteArrayAsField: Array[Byte] = {
    val out = Array.newBuilder[Byte]
    ProtoUtils.writeTag(out, ExtraEnv.EXTRA_ENV_FIELD_NUMBER, 2)
    val sz = ProtoUtils.computeStringSize(1, secondaryOutputDir)
    ProtoUtils.writeRawVarint32(out, sz)
    ProtoUtils.writeString(out, 1, secondaryOutputDir)

    out.result()
  }

  override def toString(): String =
    s"ExtraEnv(secondaryOutputDir=$secondaryOutputDir)"
}

object ExtraEnv {
  val EXTRA_ENV_FIELD_NUMBER = 1020 // ScalaPB assigned extension number
  val ENV_SECONDARY_DIR = "SCALAPB_SECONDARY_OUTPUT_DIR"
}

// Seperated to a different object since it depends on protobuf-java and expected to be run
// only in sandboxed environment.
object ExtraEnvParser {
  def fromDynamicMessage(dm: DynamicMessage): ExtraEnv = {
    new ExtraEnv(
      dm.getField(extraEnvDescriptor.findFieldByNumber(1)).asInstanceOf[String]
    )
  }

  def fromCodeGeneratorRequest(req: CodeGeneratorRequest): ExtraEnv = {
    val ll = req
      .getUnknownFields()
      .getField(ExtraEnv.EXTRA_ENV_FIELD_NUMBER)
      .getLengthDelimitedList
    if (ll.size() == 0) new ExtraEnv("")
    else
      fromDynamicMessage(
        DynamicMessage.parseFrom(
          extraEnvDescriptor,
          ll.get(0)
        )
      )
  }

  private val (extraEnvDescriptor, request): (Descriptor, Descriptor) = {
    val proto = TextFormat.parse(
      s"""
         |message_type {
         |  name: "ExtraEnv"
         |  field {
         |    name: "secondary_output_dir"
         |    number: 1
         |    label: LABEL_OPTIONAL
         |    type: TYPE_STRING
         |    json_name: "secondaryOutputDir"
         |  }
         |}
         |
         |message_type {
         |  name: "Request"
         |  field {
         |    name: "extra_env"
         |    number: ${ExtraEnv.EXTRA_ENV_FIELD_NUMBER}
         |    label: LABEL_OPTIONAL
         |    type: TYPE_MESSAGE
         |    type_name: ".ExtraEnv"
         |    json_name: "extraEnv"
         |  }
         |}
         |""".stripMargin,
      classOf[FileDescriptorProto]
    )
    val fd = FileDescriptor
      .buildFrom(proto, Array.empty)

    (fd.findMessageTypeByName("ExtraEnv"), fd.findMessageTypeByName("Request"))
  }
  private val secondaryOutputFieldDescriptor =
    extraEnvDescriptor.findFieldByName("secondary_output_dir")
}
