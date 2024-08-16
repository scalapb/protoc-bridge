package protocbridge.frontend

import java.nio.file.{Path, Paths}

/** A PluginFrontend that binds a server socket to a local interface. The plugin
  * is a batch script that invokes BridgeApp.main() method, in a new JVM with
  * the same parameters as the currently running JVM. The plugin will
  * communicate its stdin and stdout to this socket.
  */
object WindowsPluginFrontend extends SocketBasedPluginFrontend {

  protected def createShellScript(port: Int): Path = {
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
