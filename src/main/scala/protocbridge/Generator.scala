package protocbridge

// Simple definition of Maven coordinates.
case class Artifact(
    groupId: String,
    artifactId: String,
    version: String,
    crossVersion: Boolean = false
)

/** Represents a code generator invocation */
sealed trait Generator {
  def name: String

  def suggestedDependencies: Seq[Artifact]
}

/** Represents a generator built into protoc.  */
final case class BuiltinGenerator(
    name: String,
    suggestedDependencies: Seq[Artifact] = Nil
) extends Generator

final case class PluginGenerator(
    name: String,
    suggestedDependencies: Seq[Artifact],
    path: Option[String]
) extends Generator

/** Represents a generator implemented by ProtocCodeGenerator. */
final case class JvmGenerator(name: String, gen: ProtocCodeGenerator)
    extends Generator {
  def suggestedDependencies: Seq[Artifact] = gen.suggestedDependencies
}

object gens {
  // Prevent the organization name from getting shaded...
  // See https://github.com/scalapb/ScalaPB/issues/150
  private val JavaProtobufArtifact: String =
    "com+google+protobuf".replace('+', '.')

  val cpp = BuiltinGenerator("cpp")
  val csharp = BuiltinGenerator("csharp")
  val java: BuiltinGenerator = java("3.11.4")

  def java(runtimeVersion: String): BuiltinGenerator =
    BuiltinGenerator(
      "java",
      suggestedDependencies =
        Seq(Artifact(JavaProtobufArtifact, "protobuf-java", runtimeVersion))
    )

  def plugin(name: String): PluginGenerator = PluginGenerator(name, Nil, None)

  def plugin(name: String, path: String): PluginGenerator =
    PluginGenerator(name, Nil, Some(path))

  val javanano = BuiltinGenerator("javanano")
  val js = BuiltinGenerator("js")
  val objc = BuiltinGenerator("objc")
  val python = BuiltinGenerator("python")
  val ruby = BuiltinGenerator("ruby")
  val go = BuiltinGenerator("go")
  val swagger = BuiltinGenerator("swagger")
  val gateway = BuiltinGenerator("grpc-gateway")
}
