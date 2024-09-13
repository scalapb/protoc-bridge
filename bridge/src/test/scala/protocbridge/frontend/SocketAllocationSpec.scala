package protocbridge.frontend
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import java.lang.management.ManagementFactory
import java.net.ServerSocket
import scala.collection.mutable
import scala.sys.process._
import scala.util.{Failure, Success, Try}

class SocketAllocationSpec extends AnyFlatSpec with Matchers {
  it must "allocate an unused port" in {
    val repeatCount = 100000

    val currentPid = getCurrentPid
    val portConflictCount = mutable.Map[Int, Int]()

    for (i <- 1 to repeatCount) {
      if (i % 100 == 1) println(s"Running iteration $i of $repeatCount")

      val serverSocket = new ServerSocket(0) // Bind to any available port.
      try {
        val port = serverSocket.getLocalPort
        Try {
          s"lsof -i :$port -t".!!.trim
        } match {
          case Success(output) =>
            if (output.nonEmpty) {
              val pids = output.split("\n").filterNot(_ == currentPid.toString)
              if (pids.nonEmpty) {
                System.err.println("Port conflict detected on port " + port + " with PIDs: " + pids.mkString(", "))
                portConflictCount(port) = portConflictCount.getOrElse(port, 0) + 1
              }
            }
          case Failure(_) => // Ignore failure and continue
        }
      } finally {
        serverSocket.close()
      }
    }

    assert(portConflictCount.isEmpty, s"Found the following ports in use out of $repeatCount: $portConflictCount")
  }

  private def getCurrentPid: Int = {
    val jvmName = ManagementFactory.getRuntimeMXBean.getName
    val pid = jvmName.split("@")(0)
    pid.toInt
  }
}
