package protocbridge.frontend

import protocbridge.frontend.SocketBasedPluginFrontend.getFreeSocket
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.lang.management.ManagementFactory
import java.net.ServerSocket
import java.nio.file.{Files, Path}
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.util.{Failure, Success, Try}


/** PluginFrontend for Windows and macOS where a server socket is used.
 */
abstract class SocketBasedPluginFrontend extends PluginFrontend {

  case class InternalState(serverSocket: ServerSocket, shellScript: Path)

  override def prepare(
                        plugin: ProtocCodeGenerator,
                        env: ExtraEnv
                      ): (Path, InternalState) = {
    val ss = getFreeSocket()
    val sh = createShellScript(ss.getLocalPort)

    Future {
      blocking {
        // Accept a single client connection from the shell script.
        val client = ss.accept()
        try {
          val response =
            PluginFrontend.runWithInputStream(
              plugin,
              client.getInputStream,
              env
            )
          client.getOutputStream.write(response)
        } finally {
          client.close()
        }
      }
    }

    (sh, InternalState(ss, sh))
  }

  override def cleanup(state: InternalState): Unit = {
    state.serverSocket.close()
    if (sys.props.get("protocbridge.debug") != Some("1")) {
      Files.delete(state.shellScript)
    }
  }

  protected def createShellScript(port: Int): Path
}


object SocketBasedPluginFrontend {

  private lazy val currentPid: Int = {
    val jvmName = ManagementFactory.getRuntimeMXBean.getName
    val pid = jvmName.split("@")(0)
    pid.toInt
  }

  private def isSocketConflict(currentPid: Int, port: Int): Boolean = {
    import scala.sys.process._
    Try {
      s"/usr/sbin/lsof -i :$port -t".!!.trim
    } match {
      case Success(output) =>
        if (output.nonEmpty) {
          val otherPids = output.split("\n").filterNot(_ == currentPid.toString)
          otherPids.nonEmpty
        } else {
          true
        }
      case Failure(e) =>
        System.err.println(s"Failure checking if port is busy: $e")
        false
    }
  }

  @tailrec
  private def getFreeSocketForMac(currentPid: Int, attemptsLeft: Int): ServerSocket = {
    val sock = new ServerSocket(0)
    if (isSocketConflict(currentPid, sock.getLocalPort)) {
      if (attemptsLeft > 0) {
        System.out.println(s"Socket conflict on port ${sock.getLocalPort}, retry, $attemptsLeft attempts left")
        getFreeSocketForMac(currentPid, attemptsLeft - 1)
      } else {
        System.out.println(s"Socket conflict on port ${sock.getLocalPort}, no retries left, you're gonna get an error")
        sock
      }
    } else {
      sock
    }
  }

  def getFreeSocket(maxAttempts: Int = 5): ServerSocket = {
    if (!PluginFrontend.isMac) {
      // Bind to any available port.
      new ServerSocket(0)
    } else {
      //new ServerSocket(0) on mac might return a socket already in use, so here's a hack
      getFreeSocketForMac(currentPid, maxAttempts)
    }
  }
}
