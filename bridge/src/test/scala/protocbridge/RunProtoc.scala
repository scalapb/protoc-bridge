package protocbridge

import sys.process._
import scala.io.Source

object RunProtoc extends ProtocRunner[Int] {
  def run(args: Seq[String], extraEnv: Seq[(String, String)]): Int = {
    CoursierProtocCache.runProtoc("3.24.4", args, extraEnv)
  }

  // For backwards binary compatibility
  private def run(args: Seq[String]): Int = {
    CoursierProtocCache.runProtoc("3.24.4", args, Seq.empty)
  }
}
