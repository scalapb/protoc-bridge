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

/** Represents a generator built into protoc, to be used with a directory target. */
final case class BuiltinGenerator(
    name: String,
    suggestedDependencies: Seq[Artifact] = Nil
) extends Generator

final case class PluginGenerator(
    name: String,
    suggestedDependencies: Seq[Artifact],
    path: Option[String]
) extends Generator

/** Represents a generator built into protoc, to be used with a file target. */
final case class DescriptorSetGenerator() extends Generator {
  override val name = "descriptor_set"
  override val suggestedDependencies = Nil
}

/** Represents a generator implemented by ProtocCodeGenerator. */
final case class JvmGenerator(name: String, gen: ProtocCodeGenerator)
    extends Generator {
  def suggestedDependencies: Seq[Artifact] = gen.suggestedDependencies
}

/** Represents a JvmGenerator that needs to be dynamically loaded from an artifact.
  * This allows to run each JvmGenerator in its own classloader and thus avoid dependency
  * conflicts between plugins or between plugins and the container (such as sbt).
  *
  * The primary problem triggering this is that SBT ships with an old version of protobuf-java
  * that is not binary compatible with recent versions. In addition, SBT depends on ScalaPB's runtime,
  * so ScalaPB plugins can't use ScalaPB itself without running a risk of conflict.
  *
  * artifact: Artifact containing the generator class.
  * generatorClass: A scala object that implements ProtocCodeGenerator
  */
final case class SandboxedJvmGenerator(
    name: String,
    artifact: Artifact,
    generatorClass: String,
    suggestedDependencies: Seq[Artifact]
) extends Generator

object SandboxedJvmGenerator {
  def load(gen: SandboxedJvmGenerator, loader: ClassLoader): JvmGenerator = {
    val cls = loader.loadClass(gen.generatorClass)
    val module = cls.getField("MODULE$").get(null)
    val runMethod = module.getClass().getMethod("run", classOf[Array[Byte]])
    JvmGenerator(gen.name, new ProtocCodeGenerator {
      def run(request: Array[Byte]): Array[Byte] =
        runMethod.invoke(module, request).asInstanceOf[Array[Byte]]
    })
  }
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
  val descriptorSet = DescriptorSetGenerator()
}
