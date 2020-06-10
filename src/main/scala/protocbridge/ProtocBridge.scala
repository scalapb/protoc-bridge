package protocbridge

import java.io.File
import java.nio.file.Path

import protocbridge.frontend.PluginFrontend

import scala.language.implicitConversions

/** Target is a generator call and a path to output the generated files */
case class Target(
    generator: Generator,
    outputPath: File,
    options: Seq[String] = Seq.empty
)

object Target {
  import scala.language.implicitConversions
  def builtin(name: String, options: Seq[String] = Seq.empty) =
    (BuiltinGenerator(name), options)

  def apply(
      generatorAndOpts: (Generator, Seq[String]),
      outputPath: File
  ): Target = {
    apply(generatorAndOpts._1, outputPath, generatorAndOpts._2)
  }

  implicit def generatorOptsFileTupleToTarget(
      s: ((Generator, Seq[String]), File)
  ): Target =
    Target(s._1, s._2)

  implicit def generatorFileTupleToTarget(s: (Generator, File)): Target =
    Target(s._1, s._2)

  implicit def protocCodeGeneratorFile(s: (ProtocCodeGenerator, File)): Target =
    Target(s._1, s._2)

  implicit def protocCodeGeneratorOptsFile(
      s: ((ProtocCodeGenerator, Seq[String]), File)
  ): Target =
    Target(ProtocCodeGenerator.toGenerator(s._1._1), s._2, s._1._2)
}

object ProtocBridge {
  def run[A](
      protoc: Seq[String] => A,
      targets: Seq[Target],
      params: Seq[String],
  ): A = run(protoc, targets, params, PluginFrontend.newInstance)

  def run[A](
      protoc: Seq[String] => A,
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend
  ): A = run(
    protoc,
    targets,
    params,
    pluginFrontend,
    plugin =>
      throw new RuntimeException(
        s"The version of sbt-protoc you are using is incompatible with '${plugin.name}' code generator. Please update sbt-protoc to a version >= 0.99.32"
      )
  )

  /*** Runs protoc with a given set of targets.
    *
    * @param protoc a function that runs protoc with the given command line arguments.
    * @param targets a sequence of generators to invokes
    * @param params a sequence of additional params to pass to protoc
    * @param pluginFrontend frontend to use.
    * @param classLoader function that provided a sandboxed ClassLoader for an artifact.
    * @tparam A
    * @return the return value from the protoc function.
    */
  def run[A](
      protoc: Seq[String] => A,
      targets: Seq[Target],
      params: Seq[String],
      pluginFrontend: PluginFrontend,
      classLoader: SandboxedJvmGenerator => ClassLoader
  ): A = {

    // The same JvmGenerator might be passed several times, but requires separate frontends
    val targetsSuffixed = targets.zipWithIndex.map {
      case (t @ Target(gen: JvmGenerator, _, _), i) =>
        t.copy(generator = gen.copy(name = s"${gen.name}_$i"))
      case (t @ Target(gen: SandboxedJvmGenerator, _, _), i) =>
        t.copy(generator = SandboxedJvmGenerator.load(gen, classLoader(gen)))
      case (t, _) => t
    }

    val namedGenerators: Seq[(String, ProtocCodeGenerator)] =
      targetsSuffixed.collect {
        case Target(gen: JvmGenerator, _, _) =>
          (gen.name, gen.gen)
      }

    val cmdLine: Seq[String] = pluginArgs(targets) ++ targetsSuffixed.flatMap { p =>
      val maybeOptions = if(p.options.isEmpty) Nil else {
        s"--${p.generator.name}_opt=${p.options.mkString(",")}" :: Nil
      }
      s"--${p.generator.name}_out=${p.outputPath.getAbsolutePath}" :: maybeOptions
    } ++ params

    runWithGenerators(protoc, namedGenerators, cmdLine, pluginFrontend)
  }

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

    pluginsAndPaths.map {
      case (name, path) => s"--plugin=protoc-gen-$name=$path"
    }
  }

  def runWithGenerators[A](
      protoc: Seq[String] => A,
      namedGenerators: Seq[(String, ProtocCodeGenerator)],
      params: Seq[String],
      pluginFrontend: PluginFrontend = PluginFrontend.newInstance
  ): A = {

    val generatorScriptState
        : Seq[(String, (Path, pluginFrontend.InternalState))] =
      namedGenerators.map {
        case (name, plugin) => (name, pluginFrontend.prepare(plugin))
      }

    val cmdLine: Seq[String] = generatorScriptState.map {
      case (name, (scriptPath, _)) =>
        s"--plugin=protoc-gen-${name}=${scriptPath}"
    } ++ params

    try {
      protoc(cmdLine)
    } finally {
      generatorScriptState.foreach {
        case (_, (_, state)) => pluginFrontend.cleanup(state)
      }
    }
  }
}
