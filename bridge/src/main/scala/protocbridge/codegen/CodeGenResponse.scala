package protocbridge.codegen

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scala.collection.JavaConverters._

@deprecated(
  "Use protocgen.CodeGenResponse from com.thesamet.scalapb:protocgen instead",
  "0.9.0"
)
sealed trait CodeGenResponse {
  def toCodeGeneratorResponse: CodeGeneratorResponse =
    this match {
      case CodeGenResponse.Internal.Success(files) =>
        val b = CodeGeneratorResponse.newBuilder()
        b.addAllFile(files.asJava)
        b.build()
      case CodeGenResponse.Internal.Failure(msg) =>
        val b = CodeGeneratorResponse.newBuilder()
        b.setError(msg)
        b.build()
    }
}

@deprecated(
  "Use protocgen.CodeGenResponse from com.thesamet.scalapb:protocgen instead",
  "0.9.0"
)
object CodeGenResponse {
  def succeed(files: Seq[CodeGeneratorResponse.File]): CodeGenResponse =
    Internal.Success(files)

  def fail(message: String): CodeGenResponse = Internal.Failure(message)

  private object Internal {
    final case class Failure(val message: String) extends CodeGenResponse

    final case class Success(val files: Seq[CodeGeneratorResponse.File])
        extends CodeGenResponse
  }
}
