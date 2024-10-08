package protocbridge.frontend

import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.net.ServerSocket
import java.nio.file.{Files, Path, Paths}

/** A PluginFrontend that binds a server socket to a local interface. The plugin
  * is a batch script that invokes BridgeApp.main() method, in a new JVM with
  * the same parameters as the currently running JVM. The plugin will
  * communicate its stdin and stdout to this socket.
  */
object WindowsPluginFrontend extends SocketBasedPluginFrontend {
  case class InternalState(shellScript: Path, serverSocket: ServerSocket)

  override def prepare(
      plugin: ProtocCodeGenerator,
      env: ExtraEnv
  ): (Path, InternalState) = {
    val ss = new ServerSocket(0) // Bind to any available port.
    val sh = createShellScript(ss.getLocalPort)

    runWithSocket(plugin, env, ss)

    (sh, InternalState(sh, ss))
  }

  override def cleanup(state: InternalState): Unit = {
    state.serverSocket.close()
    if (sys.props.get("protocbridge.debug") != Some("1")) {
      Files.delete(state.shellScript)
    }
  }

  private def createShellScript(port: Int): Path = {
    val classPath =
      Paths.get(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    val classPathBatchString = classPath.toString.replace("%", "%%")
    val batchFile = PluginFrontend.createTempFile(
      ".bat",
      s"""@echo off
          |"${sys
          .props(
            "java.home"
          )}\\bin\\java.exe" -cp "$classPathBatchString" ${classOf[
          BridgeApp
        ].getName} $port
        """.stripMargin
    )
    batchFile
  }
}
