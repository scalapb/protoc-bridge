package protocbridge

import scala.io.Source
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

abstract class ProtocRunner[ExitCode] {
  self =>
  def run(args: Seq[String], extraEnv: Seq[(String, String)]): ExitCode

  /** Returns a new ProtocRunner that is executed after this value. The exit codes
    *  are combined into a tuple.
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

  def apply(executable: String): ProtocRunner[Int] = ProtocRunner.fromFunction {
    case (args, extraEnv) =>
      Process(
        command = (maybeNixDynamicLinker().toSeq :+ executable) ++ args,
        cwd = None,
        extraEnv: _*
      ).!
  }
}
