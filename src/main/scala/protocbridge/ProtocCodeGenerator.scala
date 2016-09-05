package protocbridge

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorResponse, CodeGeneratorRequest}

/** This is the interface that code generators need to implement. */
trait ProtocCodeGenerator {
  def registerExtensions(p: ExtensionRegistry): Unit = {}

  def run(request: CodeGeneratorRequest): CodeGeneratorResponse

  def suggestedDependencies: Seq[Artifact] = Nil
}

object ProtocCodeGenerator {
  import scala.language.implicitConversions

  implicit def toGenerator(p: ProtocCodeGenerator): Generator = {
    JvmGenerator("jvm_" + scala.util.Random.alphanumeric.take(8).mkString, p)
  }
}
