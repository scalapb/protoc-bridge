package protocbridge

import java.io.File

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
