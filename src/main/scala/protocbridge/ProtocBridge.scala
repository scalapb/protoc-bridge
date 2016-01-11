package protocbridge

import java.nio.file.Path

object ProtocBridge {

  /*** Runs protoc bridges to a given set of plugins.
    *
    * @param protoc a function that runs protoc with the given command line arguments.
    * @param params a sequence of params to pass to protoc
    * @param generatorParams a sequence of code generator calls
    * @param pluginFrontend operatng system-dependent frontend to use.
    * @tparam A
    * @return the return value from the protoc function.
    */
  def run[A](protoc: Seq[String] => A,
             params: Seq[String],
             generatorParams: Seq[GeneratorParam],
             pluginFrontend: PluginFrontend = PluginFrontend.apply()): A = {

    val namedGenerators: Seq[(String, ProtocCodeGenerator)] =
      generatorParams.collect {
        case GeneratorParam(p: BridgedGenerator, _) =>
          (p.name, p.gen)
      }

    val cmdLine: Seq[String] = generatorParams.map {
      p =>
        s"--${p.name}_out=${p.options.mkString(",")}:${p.outputPath.getAbsolutePath}"
    } ++ params

    runWithGenerators(protoc, cmdLine, namedGenerators, pluginFrontend)
  }

  def runWithGenerators[A](protoc: Seq[String] => A,
                           params: Seq[String],
                           namedGenerators: Seq[(String, ProtocCodeGenerator)],
                           pluginFrontend: PluginFrontend = PluginFrontend.apply()): A = {

    val generatorScriptState: Seq[(String, (Path, pluginFrontend.InternalState))] =
      namedGenerators.map {
        case (name, plugin) => (name, pluginFrontend.prepare(plugin))
      }

    val cmdLine: Seq[String] = generatorScriptState.map {
      case (name, (scriptPath, _)) => s"--plugin=protoc-gen-${name}=${scriptPath}"
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

