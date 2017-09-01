package protocbridge

// Simple definition of Maven coordinates.
case class Artifact(groupId: String,
                    artifactId: String,
                    version: String,
                    crossVersion: Boolean = false)

/** Represents a code generator invocation */
sealed trait Generator {
  def name: String

  def suggestedDependencies: Seq[Artifact]
}

/** Represents a generator built into protoc.  */
final case class BuiltinGenerator(name: String, suggestedDependencies: Seq[Artifact] = Nil) extends Generator

/** Represents a generator implemented by ProtocCodeGenerator. */
final case class JvmGenerator(name: String, gen: ProtocCodeGenerator) extends Generator {
  def suggestedDependencies: Seq[Artifact] = gen.suggestedDependencies
}

object gens {
  // Prevent the organization name from getting shaded...
  // See https://github.com/scalapb/ScalaPB/issues/150
  private val JavaProtobufArtifact: String = "com+google+protobuf".replace('+', '.')

  val cpp = BuiltinGenerator("cpp")
  val csharp = BuiltinGenerator("csharp")
  val java: BuiltinGenerator = java("3.4.0")

  def java(runtimeVersion: String): BuiltinGenerator = BuiltinGenerator("java",
    suggestedDependencies = Seq(Artifact(JavaProtobufArtifact, "protobuf-java", runtimeVersion)))

  val javanano = BuiltinGenerator("javanano")
  val js = BuiltinGenerator("js")
  val objc = BuiltinGenerator("objc")
  val python = BuiltinGenerator("python")
  val ruby = BuiltinGenerator("ruby")
}
