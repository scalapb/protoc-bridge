package protocgen

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scala.collection.JavaConverters._

sealed trait CodeGenResponse {
  def toCodeGeneratorResponse: CodeGeneratorResponse =
    this match {
      case CodeGenResponse.Internal.Success(files, features) =>
        val b = CodeGeneratorResponse.newBuilder()
        b.addAllFile(files.asJava)
        b.setSupportedFeatures(features.map(_.getNumber()).sum)
        b.build()
      case CodeGenResponse.Internal.Failure(msg) =>
        val b = CodeGeneratorResponse.newBuilder()
        b.setError(msg)
        b.build()
    }
}

object CodeGenResponse {
  def succeed(files: Seq[CodeGeneratorResponse.File]): CodeGenResponse =
    Internal.Success(files, Set())

  def succeed(
      files: Seq[CodeGeneratorResponse.File],
      supportedFeatures: Set[CodeGeneratorResponse.Feature]
  ): CodeGenResponse =
    Internal.Success(files, supportedFeatures)

  def fail(message: String): CodeGenResponse = Internal.Failure(message)

  private object Internal {
    final case class Failure(val message: String) extends CodeGenResponse

    final case class Success(
        val files: Seq[CodeGeneratorResponse.File],
        supportedFeatures: Set[CodeGeneratorResponse.Feature]
    ) extends CodeGenResponse
  }
}
