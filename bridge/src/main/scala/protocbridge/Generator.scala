package protocbridge

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
  * This mechanism is needed because SBT ships with an old version of protobuf-java
  * that is not binary compatible with recent versions. In addition, SBT depends on
  * ScalaPB's runtime, so ScalaPB plugins can't use ScalaPB itself without running a
  * risk of conflict.
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
  def load(
      gen: SandboxedJvmGenerator,
      loader: ClassLoader
  ): ProtocCodeGenerator = {
    val cls = loader.loadClass(gen.generatorClass)
    val module = cls.getField("MODULE$").get(null)
    val runMethod = module.getClass().getMethod("run", classOf[Array[Byte]])
    new ProtocCodeGenerator {
      def run(request: Array[Byte]): Array[Byte] =
        runMethod.invoke(module, request).asInstanceOf[Array[Byte]]
    }
  }
}
