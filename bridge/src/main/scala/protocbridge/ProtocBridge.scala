package protocbridge

import java.io.File
import java.nio.file.Path

import protocbridge.frontend.PluginFrontend

import scala.language.implicitConversions
import java.nio.file.Paths
import java.nio.file.Files

object ProtocBridge {

  /** Runs protoc with a given set of targets.
    *
    * @param protoc a function that runs protoc with the given command line arguments.
    * @param targets a sequence of generators to invokes
    * @param params a sequence of additional params to pass to protoc
    * @param pluginFrontend frontend to use.
    * @param classLoader function that provided a sandboxed ClassLoader for an artifact.
    * @tparam A
    * @return the return value from the protoc function.
    */
  def run[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend,
      classLoader: Artifact => ClassLoader
  ): ExitCode = {

    // The same JvmGenerator might be passed several times, but requires separate frontends
    val targetsSuffixed = targets.zipWithIndex.map {
      case (t @ Target(gen: JvmGenerator, _, _), i) =>
        t.copy(generator = gen.copy(name = s"${gen.name}_$i"))
      case (t @ Target(gen: SandboxedJvmGenerator, _, _), i) =>
        val codeGen =
          gen.resolver(classLoader(gen.artifact))
        t.copy(generator =
          JvmGenerator(name = codeGen.name + s"_$i", gen = codeGen)
        )
      case (t, _) => t
    }

    val namedGenerators: Seq[(String, ProtocCodeGenerator)] =
      targetsSuffixed.collect { case Target(gen: JvmGenerator, _, _) =>
        (gen.name, gen.gen)
      }

    val cmdLine: Seq[String] = pluginArgs(targets) ++ targetsSuffixed.flatMap {
      p =>
        val maybeOptions =
          if (p.options.isEmpty) Nil
          else {
            s"--${p.generator.name}_opt=${p.options.mkString(",")}" :: Nil
          }
        s"--${p.generator.name}_out=${p.outputPath.getAbsolutePath}" :: maybeOptions
    } ++ params

    runWithGenerators(protoc, namedGenerators, cmdLine, pluginFrontend)
  }

  // For testing.
  def run[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): ExitCode = run[ExitCode](
    protoc,
    targets,
    params,
    pluginFrontend,
    (art: Artifact) =>
      throw new RuntimeException(
        s"Unale to load sandboxed plugin for ${art} since ClassLoader was not provided. If " +
          "using sbt-protoc, please update to version 1.0.0-RC5 or later."
      )
  )

  // For testing.
  def run[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String]
  ): ExitCode = run[ExitCode](
    protoc,
    targets,
    params,
    PluginFrontend.newInstance
  )

  private def pluginArgs(targets: Seq[Target]): Seq[String] = {
    val pluginsAndPaths: Seq[(String, String)] = targets.collect {
      case Target(PluginGenerator(pluginName, _, Some(pluginPath)), _, _) =>
        (pluginName, pluginPath)
    }.distinct

    val pluginsWithDifferentPaths =
      pluginsAndPaths.groupBy(_._1).values.collect {
        case (pluginName, _) :: rest if rest.nonEmpty => pluginName
      }

    if (pluginsWithDifferentPaths.nonEmpty) {
      throw new RuntimeException(
        "Different paths found for the plugin: " + pluginsWithDifferentPaths
          .mkString(",")
      )
    }

    pluginsAndPaths.map { case (name, path) =>
      s"--plugin=protoc-gen-$name=$path"
    }
  }

  def runWithGenerators[ExitCode](
      protoc: ProtocRunner[ExitCode],
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String]
  ): ExitCode = runWithGenerators(
    protoc,
    namedGenerators,
    params,
    PluginFrontend.newInstance
  )

  def runWithGenerators[ExitCode](
      protoc: ProtocRunner[ExitCode],
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): ExitCode = {

    import collection.JavaConverters._
    val secondaryOutputDir = Files
      .createTempDirectory("protocbridge-secondary")
      .toAbsolutePath()
    val extraEnv = new ExtraEnv(
      secondaryOutputDir = secondaryOutputDir.toString()
    )

    val generatorScriptState
        : Seq[(String, (Path, pluginFrontend.InternalState))] =
      namedGenerators.map { case (name, plugin) =>
        (name, pluginFrontend.prepare(plugin, extraEnv))
      }

    val cmdLine: Seq[String] = generatorScriptState.map {
      case (name, (scriptPath, _)) =>
        s"--plugin=protoc-gen-${name}=${scriptPath}"
    } ++ params

    try {
      protoc.run(
        cmdLine,
        extraEnv.toEnvMap.toSeq
      )
    } finally {
      if (sys.env.getOrElse("PROTOCBRIDGE_NO_CLEANUP", "0") == "0") {
        generatorScriptState.foreach { case (_, (_, state)) =>
          pluginFrontend.cleanup(state)
        }
        secondaryOutputDir.toFile().listFiles().foreach(_.delete())
        secondaryOutputDir.toFile.delete()
      }
    }
  }

  // Deprecated methods
  // Left bo binary backwards compatibility
  @deprecated(
    "Please use run() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  private[this] def run[ExitCode](
      protoc: Seq[String] => ExitCode,
      targets: Seq[Target],
      params: Seq[String]
  ): ExitCode = run(protoc, targets, params, PluginFrontend.newInstance)

  @deprecated(
    "Please use run() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  def run[ExitCode](
      protoc: Seq[String] => ExitCode,
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): ExitCode =
    run(
      protoc,
      targets,
      params,
      pluginFrontend,
      artifact =>
        throw new RuntimeException(
          s"The version of sbt-protoc you are using is incompatible with '${artifact}' code generator. Please update sbt-protoc to a version >= 0.99.33"
        )
    )

  @deprecated(
    "Please use run() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  def run[ExitCode](
      protoc: Seq[String] => ExitCode,
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend,
      classLoader: Artifact => ClassLoader
  ): ExitCode = run(
    ProtocRunner(protoc),
    targets,
    params,
    pluginFrontend,
    classLoader
  )

  @deprecated(
    "Please use run() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  def runWithGenerators[ExitCode](
      protoc: Seq[String] => ExitCode,
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): ExitCode = runWithGenerators(
    ProtocRunner(protoc),
    namedGenerators,
    params,
    pluginFrontend
  )

  @deprecated(
    "Please use run() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  def runWithGenerators[ExitCode](
      protoc: Seq[String] => ExitCode,
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String]
  ): ExitCode = runWithGenerators(
    ProtocRunner(protoc),
    namedGenerators,
    params,
    PluginFrontend.newInstance
  )
}
