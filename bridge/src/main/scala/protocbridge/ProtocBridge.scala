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
    * @param classLoader function that provided a sandboxed ClassLoader for an artifact.
    * @tparam A
    * @return the return value from the protoc function.
    */
  def execute[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String],
      classLoader: Artifact => ClassLoader
  ): ExitCode =
    execute(protoc, targets, params, PluginFrontend.newInstance, classLoader)

  private[protocbridge] def execute[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend,
      classLoader: Artifact => ClassLoader
  ): ExitCode = {

    // The same JvmGenerator or PluginGenerator might be passed several times with different options
    val targetsSuffixed =
      targets
        .groupBy(_.generator.name)
        .values
        .flatMap { targets =>
          // don't add suffix if we have only one generator for that name
          if (targets.length == 1) targets
          else {
            targets.zipWithIndex.map {
              case (t @ Target(gen: JvmGenerator, _, _), i) =>
                t.copy(generator = gen.copy(name = s"${gen.name}_$i"))
              case (t @ Target(gen: SandboxedJvmGenerator, _, _), i) =>
                val codeGen =
                  gen.resolver(classLoader(gen.artifact))
                t.copy(generator =
                  JvmGenerator(name = codeGen.name + s"_$i", gen = codeGen)
                )
              case (t @ Target(gen: PluginGenerator, _, _), i) =>
                t.copy(generator = gen.copy(name = s"${gen.name}_$i"))
              case (t, _) => t
            }
          }
        }
        .toSeq

    val namedGenerators: Seq[(String, ProtocCodeGenerator)] =
      targetsSuffixed.collect { case Target(gen: JvmGenerator, _, _) =>
        (gen.name, gen.gen)
      }

    val cmdLine: Seq[String] =
      pluginArgs(targetsSuffixed) ++ (targetsSuffixed).flatMap { p =>
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
  private[protocbridge] def execute[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): ExitCode = execute[ExitCode](
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

  private[protocbridge] def execute[ExitCode](
      protoc: ProtocRunner[ExitCode],
      targets: Seq[Target],
      params: Seq[String]
  ): ExitCode = execute[ExitCode](
    protoc,
    targets,
    params,
    PluginFrontend.newInstance
  )

  private def pluginArgs(targets: Seq[Target]): Seq[String] =
    targets.collect { case Target(PluginGenerator(name, _, Some(path)), _, _) =>
      s"--plugin=protoc-gen-$name=$path"
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

  private def runWithGenerators[ExitCode](
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
  @deprecated(
    "Please use execute() overload that takes ProtocRunner. Secondary outputs will fail to work.",
    "0.9.0"
  )
  def run[A](
      protoc: Seq[String] => A,
      targets: Seq[Target],
      params: Seq[String]
  ): A = run(protoc, targets, params, PluginFrontend.newInstance)

  @deprecated(
    "Please use execute() overload that takes ProtocRunner. Secondary outputs will fail to work.",
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
    "Please use execute(). Secondary outputs will fail to work.",
    "0.9.0"
  )
  def run[ExitCode](
      protoc: Seq[String] => ExitCode,
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend,
      classLoader: Artifact => ClassLoader
  ): ExitCode = execute(
    ProtocRunner(protoc),
    targets,
    params,
    pluginFrontend,
    classLoader
  )

  @deprecated(
    "Please use execute(). Secondary outputs will fail to work.",
    "0.9.0"
  )
  def runWithGenerators[ExitCode](
      protoc: Seq[String] => ExitCode,
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String],
      pluginFrontend: PluginFrontend = PluginFrontend.newInstance
  ): ExitCode = runWithGenerators(
    ProtocRunner(protoc),
    namedGenerators,
    params,
    pluginFrontend
  )
}
