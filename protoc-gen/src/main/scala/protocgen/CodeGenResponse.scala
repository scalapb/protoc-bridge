package protocgen

import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import scala.collection.JavaConverters._

sealed trait CodeGenResponse {
  def toCodeGeneratorResponse: CodeGeneratorResponse =
    this match {
      case value: CodeGenResponse.Internal.Success =>
        val b = CodeGeneratorResponse.newBuilder()
        b.addAllFile(value.files.asJava)
        b.setSupportedFeatures(value.supportedFeatures.map(_.getNumber()).sum)
        b.setUnknownFields(
          // https://github.com/protocolbuffers/protobuf/blob/1f60d67437d7f57700/src/google/protobuf/compiler/plugin.proto#L105-L115
          UnknownFieldSet
            .newBuilder()
            .addField(
              3,
              UnknownFieldSet.Field
                .newBuilder()
                .addVarint(
                  value.minimumEdition
                )
                .build()
            )
            .addField(
              4,
              UnknownFieldSet.Field
                .newBuilder()
                .addVarint(
                  value.maximumEdition
                )
                .build()
            )
            .build()
        )
        b.build()
      case CodeGenResponse.Internal.Failure(msg) =>
        val b = CodeGeneratorResponse.newBuilder()
        b.setError(msg)
        b.build()
    }
}

object CodeGenResponse {
  def succeed(files: Seq[CodeGeneratorResponse.File]): CodeGenResponse =
    Internal.Success(files, Set(), 0, 0)

  def succeed(
      files: Seq[CodeGeneratorResponse.File],
      supportedFeatures: Set[CodeGeneratorResponse.Feature]
  ): CodeGenResponse =
    Internal.Success(files, supportedFeatures, 0, 0)

  def succeed(
      files: Seq[CodeGeneratorResponse.File],
      supportedFeatures: Set[CodeGeneratorResponse.Feature],
      minimumEdition: Int,
      maximumEdition: Int
  ): CodeGenResponse =
    Internal.Success(files, supportedFeatures, minimumEdition, maximumEdition)

  def fail(message: String): CodeGenResponse = Internal.Failure(message)

  private object Internal {
    final case class Failure(val message: String) extends CodeGenResponse

    final case class Success(
        files: Seq[CodeGeneratorResponse.File],
        supportedFeatures: Set[CodeGeneratorResponse.Feature],
        minimumEdition: Int,
        maximumEdition: Int
    ) extends CodeGenResponse
  }
}
