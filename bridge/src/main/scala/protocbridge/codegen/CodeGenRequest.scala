package protocbridge.codegen

import scala.collection.JavaConverters._

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.DescriptorProtos.FileDescriptorProto

@deprecated(
  "Use protocgen.CodeGenRequest from com.thesamet.scalapb:protocgen instead",
  "0.9.0"
)
case class CodeGenRequest(
    parameter: String,
    filesToGenerate: Seq[FileDescriptor],
    allProtos: Seq[FileDescriptor],
    compilerVersion: Option[PluginProtos.Version]
)

@deprecated(
  "Use protocgen.CodeGenRequest from com.thesamet.scalapb:protocgen instead",
  "0.9.0"
)
object CodeGenRequest {
  def apply(req: CodeGeneratorRequest) = {
    val filesMap = fileDescriptorsByName(
      req.getProtoFileList().asScala.toVector
    )
    new CodeGenRequest(
      parameter = req.getParameter(),
      filesToGenerate =
        req.getFileToGenerateList().asScala.toVector.map(filesMap),
      allProtos = filesMap.values.toVector,
      compilerVersion =
        if (req.hasCompilerVersion()) Some(req.getCompilerVersion()) else None
    )
  }

  def fileDescriptorsByName(
      fileProtos: Seq[FileDescriptorProto]
  ): Map[String, FileDescriptor] =
    fileProtos.foldLeft[Map[String, FileDescriptor]](Map.empty) {
      case (acc, fp) =>
        val deps = fp.getDependencyList.asScala.map(acc)
        acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
    }
}
