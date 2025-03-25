package protocbridge.frontend

import protocbridge.frontend.SocketBasedPluginFrontend.getFreeSocket
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.net.{InetAddress, ServerSocket}
import java.nio.file.{Files, Path}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}


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

  /**
   * for mac we need to specify an address, otherwise it uses ANY (*.* in netstat)
   * which does not conflict with existing 'localhost' sockets,
   * resulting in a conflict later on in MacPluginFrontend
   */
  def getFreeSocket(): ServerSocket = {
    if (!PluginFrontend.isMac) {
      // Bind to any available port.
      new ServerSocket(0)
    } else {
      new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    }
  }
}
