package protocbridge

import sys.process._
import scala.io.Source

object RunProtoc {
  def run(args: Seq[String]): Int =
    CoursierProtocCache.runProtoc("3.11.4", args)
}
