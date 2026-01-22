package protocbridge

import java.nio.file.Files
import scala.io.Source
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

abstract class ProtocRunner[ExitCode] {
  self =>
  def run(args: Seq[String], extraEnv: Seq[(String, String)]): ExitCode

  /** Returns a new ProtocRunner that is executed after this value. The exit
    * codes are combined into a tuple.
    */
  def zip[E](
      other: ProtocRunner[E]
  ): ProtocRunner[(ExitCode, E)] = ProtocRunner.fromFunction {
    (args, extraEnv) => (run(args, extraEnv), other.run(args, extraEnv))
  }

  /** Returns a new ProtocRunner that maps the exit code of this runner. */
  def map[E](f: ExitCode => E): ProtocRunner[E] = ProtocRunner.fromFunction {
    (args, extraEnv) => f(run(args, extraEnv))
  }
}

object ProtocRunner {

  /** Makes a ProtocRunner that runs the given command line, but discards the
    * extra environment. Exists only for backwards compatility.
    */
  private[protocbridge] def apply[A](f: Seq[String] => A): ProtocRunner[A] =
    new ProtocRunner[A] {
      def run(args: Seq[String], extraEnv: Seq[(String, String)]): A = f(args)
    }

  private[this] def detectedOs: String =
    SystemDetector.normalizedOs(SystemDetector.detectedClassifier())

  def fromFunction[A](
      f: (Seq[String], Seq[(String, String)]) => A
  ): ProtocRunner[A] =
    new ProtocRunner[A] {
      def run(args: Seq[String], extraEnv: Seq[(String, String)]): A =
        f(args, extraEnv)
    }

  def maybeNixDynamicLinker(): Option[String] =
    detectedOs match {
      case "linux" =>
        sys.env.get("NIX_CC").map { nixCC =>
          val source = Source.fromFile(nixCC + "/nix-support/dynamic-linker")
          val linker = source.mkString.trim()
          source.close()
          linker
        }
      case _ =>
        None
    }

  def maybeBoxArgsInFile[T](args: Seq[String])(withArgs: Seq[String] => T): T =
    detectedOs match {
      case "windows" =>
        // The default command line length limit is 32767, which we might exceed.
        // See also https://devblogs.microsoft.com/oldnewthing/20031210-00/?p=41553
        // As of protobuf v3.5.0, you can pass arguments via a file instead with @<filename>.
        // Arguments in the file are delimited by a newline and not escaped in any way.
        val argumentFile = Files.createTempFile("scalapb-arguments-", ".txt")

        try {
          val writer = Files.newBufferedWriter(argumentFile)

          try {
            for (arg <- args) {
              writer.write(arg)
              writer.write("\n")
            }
          } finally {
            writer.close()
          }

          val fileArgument = s"@${argumentFile.toString}"
          withArgs(Seq(fileArgument))
        } finally {
          Files.delete(argumentFile)
        }
      case _ =>
        // No special handling: just use the arguments as-is.
        withArgs(args)
    }

  // This version of maybeNixDynamicLinker() finds ld-linux and also uses it
  // to verify that the executable is dynamic. Newer version (>=3.23.0) of
  // protoc are static, and thus do not load with ld-linux.
  def maybeNixDynamicLinker(executable: String): Option[String] =
    maybeNixDynamicLinker().filter { linker =>
      Process(command = Seq(linker, "--verify", executable)).! == 0
    }

  def apply(executable: String): ProtocRunner[Int] = ProtocRunner.fromFunction {
    case (args, extraEnv) =>
      maybeBoxArgsInFile(args) { args =>
        Process(
          command =
            (maybeNixDynamicLinker(executable).toSeq :+ executable) ++ args,
          cwd = None,
          extraEnv: _*
        ).!
      }
  }

  // Transforms the given protoc runner to a new runner that writes the
  // options into a temporary file and passes the file to `protoc` as an `@`
  // parameter.
  def withParametersAsFile[T](underlying: ProtocRunner[T]): ProtocRunner[T] =
    fromFunction { (args, extraEnv) =>
      {
        val argumentFile = Files.createTempFile("protoc-args-", ".txt")
        try {
          val writer = Files.newBufferedWriter(argumentFile)

          try {
            args.foreach { arg =>
              writer.write(arg)
              writer.write('\n')
            }
          } finally {
            writer.close()
          }

          val fileArgument = s"@${argumentFile.toString}"
          underlying.run(Seq(fileArgument), extraEnv)
        } finally {
          Files.delete(argumentFile)
        }
      }
    }
}
