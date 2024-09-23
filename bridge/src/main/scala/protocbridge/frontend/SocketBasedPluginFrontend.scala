package protocbridge.frontend

import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.net.ServerSocket
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}

/** PluginFrontend for Windows and macOS where a server socket is used.
  */
abstract class SocketBasedPluginFrontend extends PluginFrontend {

  protected def runWithSocket(
      plugin: ProtocCodeGenerator,
      env: ExtraEnv,
      serverSocket: ServerSocket
  ): Unit = {
    Future {
      blocking {
        // Accept a single client connection from the shell script.
        val client = serverSocket.accept()
        // It's found on macOS that a `junixsocket` domain socket server
        // might not receive the EOF sent by the other end, leading to a hang:
        // https://github.com/scalapb/protoc-bridge/issues/379
        // However, confusingly, adding an arbitrary read timeout resolves the issue.
        // We thus add a read timeout of 1 minute here, which should be more than enough.
        // It also helps to prevent an infinite hang on both Windows and macOS due to
        // unexpected issues.
        // client.setSoTimeout(60000)
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
  }
}
