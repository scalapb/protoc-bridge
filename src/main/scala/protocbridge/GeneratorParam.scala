package protocbridge

import java.io.File

// Simple definition of Maven coordinates.
case class Artifact(groupId: String,
                    artifactId: String,
                    version: String,
                    crossVersion: Boolean = false)

// Represents any protoc generator
sealed trait Generator {
  def name: String
  def options: Seq[String]
}

/** Represents a generator built into protoc.
  */
final case class BuiltinGenerator(name: String, options: Seq[String] = Nil, suggestedDependencies: Seq[Artifact] = Nil) extends Generator


/** Represents a generator defined by ProtocCodeGenerator.
  */
final case class BridgedGenerator(name: String, gen: ProtocCodeGenerator, options: Seq[String] = Nil) extends Generator

/** Represents a protoc command line parameter to invoke a Generator.
  */
case class GeneratorParam(gen: Generator,
                          outputPath: File) {
  def name: String = gen.name

  def options: Seq[String] = gen.options

  def suggestedDependencies = gen match {
    case p: BuiltinGenerator => p.suggestedDependencies
    case p: BridgedGenerator => p.gen.suggestedDependencies
  }
}

object GeneratorParam {

  object builtin {
    val cpp = BuiltinGenerator("cpp")
    val csharp = BuiltinGenerator("csharp")
    val java: BuiltinGenerator = java("2.6.1")
    def java(runtimeVersion: String): BuiltinGenerator = BuiltinGenerator("java",
      suggestedDependencies = Seq(Artifact("com.google.protobuf", "protobuf-java", runtimeVersion)))
    val javanano = BuiltinGenerator("javanano")
    val js = BuiltinGenerator("js")
    val objc = BuiltinGenerator("objc")
    val python = BuiltinGenerator("python")
    val ruby = BuiltinGenerator("ruby")
  }
}
