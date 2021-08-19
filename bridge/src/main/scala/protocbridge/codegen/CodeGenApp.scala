package protocbridge.codegen

import protocbridge.ProtocCodeGenerator
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.CodedInputStream
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.DescriptorProtos.FileDescriptorProto

/** CodeGenApp provides a higher-level Scala API to build protoc code
  * generators.
  *
  * As a code generator author, you need to optionally provide a
  * `registerExtensions` function to register any extensions needed for parsing
  * the CodeGeneratorRequest.
  *
  * The implement the function process that takes a CodeGenRequest and returns a
  * CodeGenResponse. These classes provides higher-level, idiomatic access to
  * the request and response used by protoc.
  */
@deprecated(
  "Use protocgen.CodeGenApp from com.thesamet.scalapb:protocgen instead",
  "0.9.0"
)
trait CodeGenApp extends ProtocCodeGenerator {
  def registerExtensions(registry: ExtensionRegistry): Unit = {}

  def process(request: CodeGenRequest): CodeGenResponse

  final def main(args: Array[String]): Unit = {
    System.out.write(run(CodedInputStream.newInstance(System.in)))
  }

  final override def run(req: Array[Byte]): Array[Byte] =
    run(CodedInputStream.newInstance(req))

  final def run(input: CodedInputStream): Array[Byte] = {
    try {
      val registry = ExtensionRegistry.newInstance()
      registerExtensions(registry)
      val request = CodeGenRequest(
        CodeGeneratorRequest.parseFrom(input, registry)
      )
      process(request).toCodeGeneratorResponse.toByteArray()
    } catch {
      case t: Throwable =>
        CodeGeneratorResponse
          .newBuilder()
          .setError(t.toString)
          .build()
          .toByteArray
    }
  }
}
