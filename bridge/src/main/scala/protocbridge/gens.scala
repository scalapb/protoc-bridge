package protocbridge

object gens {
  // Prevent the organization name from getting shaded...
  // See https://github.com/scalapb/ScalaPB/issues/150
  private val JavaProtobufArtifact: String =
    "com+google+protobuf".replace('+', '.')

  val cpp = BuiltinGenerator("cpp")
  val csharp = BuiltinGenerator("csharp")
  val java: BuiltinGenerator = java("3.24.4")

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
  val kotlin: BuiltinGenerator = kotlin("3.24.4")
  def kotlin(runtimeVersion: String): BuiltinGenerator =
    BuiltinGenerator(
      "kotlin",
      suggestedDependencies =
        Seq(Artifact(JavaProtobufArtifact, "protobuf-kotlin", runtimeVersion))
    )

  val js = BuiltinGenerator("js")
  val objc = BuiltinGenerator("objc")
  val python = BuiltinGenerator("python")
  val ruby = BuiltinGenerator("ruby")
  val go = BuiltinGenerator("go")
  val swagger = BuiltinGenerator("swagger")
  val gateway = BuiltinGenerator("grpc-gateway")
  val descriptorSet = DescriptorSetGenerator()
}
