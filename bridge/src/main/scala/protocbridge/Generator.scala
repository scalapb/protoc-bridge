package protocbridge

/** Represents a code generator invocation */
sealed trait Generator {
  def name: String

  def suggestedDependencies: Seq[Artifact]
}

/** Represents a generator built into protoc, to be used with a directory
  * target.
  */
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

/** Represents a JvmGenerator that needs to be dynamically loaded from an
  * artifact. This allows to run each JvmGenerator in its own classloader and
  * thus avoid dependency conflicts between plugins or between plugins and the
  * container (such as sbt).
  *
  * This mechanism is needed because SBT ships with an old version of
  * protobuf-java that is not binary compatible with recent versions. In
  * addition, SBT depends on ScalaPB's runtime, so ScalaPB plugins can't use
  * ScalaPB itself without running a risk of conflict.
  *
  * artifact: Artifact containing the generator class. resolver: Using a
  * ClassLoader, return a new instance of a ProtocCodeGenerator.
  */
final case class SandboxedJvmGenerator private (
    name: String,
    artifact: Artifact,
    suggestedDependencies: Seq[Artifact],
    resolver: ClassLoader => ProtocCodeGenerator
) extends Generator {
  private[protocbridge] def this(
      name: String,
      artifact: Artifact,
      generatorClass: String,
      suggestedDependencies: Seq[Artifact]
  ) =
    this(
      name,
      artifact,
      suggestedDependencies,
      SandboxedJvmGenerator.load(generatorClass, _)
    )
}

object SandboxedJvmGenerator {

  /** Instantiates a SandboxedJvmGenerator that loads an object named
    * generatorClass
    */
  def forModule(
      name: String,
      artifact: Artifact,
      generatorClass: String,
      suggestedDependencies: Seq[Artifact]
  ): SandboxedJvmGenerator =
    SandboxedJvmGenerator(
      name,
      artifact,
      suggestedDependencies,
      SandboxedJvmGenerator.load(generatorClass, _)
    )

  /** Instantiates a SandboxedJvmGenerator that uses a class loader to load a
    * generator
    */
  def forResolver(
      name: String,
      artifact: Artifact,
      suggestedDependencies: Seq[Artifact],
      resolver: ClassLoader => ProtocCodeGenerator
  ): SandboxedJvmGenerator =
    SandboxedJvmGenerator(
      name: String,
      artifact: Artifact,
      suggestedDependencies: Seq[Artifact],
      resolver: ClassLoader => ProtocCodeGenerator
    )

  // kept for binary compatiblity with 0.9.0-RC1
  private[this] def apply(
      name: String,
      artifact: Artifact,
      generatorClass: String,
      suggestedDependencies: Seq[Artifact]
  ): SandboxedJvmGenerator =
    forModule(
      name,
      artifact,
      generatorClass,
      suggestedDependencies
    )

  def load(
      generatorClass: String,
      loader: ClassLoader
  ): ProtocCodeGenerator = {
    val cls = loader.loadClass(generatorClass)
    val module = cls.getField("MODULE$").get(null)
    val runMethod = module.getClass().getMethod("run", classOf[Array[Byte]])
    new ProtocCodeGenerator {
      def run(request: Array[Byte]): Array[Byte] =
        runMethod.invoke(module, request).asInstanceOf[Array[Byte]]
    }
  }
}
