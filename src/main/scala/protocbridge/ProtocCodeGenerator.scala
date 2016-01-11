package protocbridge

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorResponse, CodeGeneratorRequest}

trait ProtocCodeGenerator {
  def name: String

  def registerExtensions(p: ExtensionRegistry): Unit = {}

  def run(request: CodeGeneratorRequest): CodeGeneratorResponse

  def suggestedDependencies: Seq[Artifact] = Nil

  def toGenerator(options: Seq[String] = Nil): Generator = BridgedGenerator(name, this, options)
}
