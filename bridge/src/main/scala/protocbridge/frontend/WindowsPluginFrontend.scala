package protocbridge.frontend

import java.net.ServerSocket
import java.nio.file.{Files, Path, Paths}

import protocbridge.ExtraEnv
import protocbridge.ProtocCodeGenerator

import scala.concurrent.blocking

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/** A PluginFrontend that binds a server socket to a local interface. The plugin
  * is a batch script that invokes BridgeApp.main() method, in a new JVM with
  * the same parameters as the currently running JVM. The plugin will
  * communicate its stdin and stdout to this socket.
  */
object WindowsPluginFrontend extends PluginFrontend {

  case class InternalState(batFile: Path)

  override def prepare(
      plugin: ProtocCodeGenerator,
      env: ExtraEnv
  ): (Path, InternalState) = {
    val ss = new ServerSocket(0)
    val state = createWindowsScript(ss.getLocalPort)

    Future {
      blocking {
        val client = ss.accept()
        val response =
          PluginFrontend.runWithInputStream(plugin, client.getInputStream, env)
        client.getOutputStream.write(response)
        client.close()
        ss.close()
      }
    }

    (state.batFile, state)
  }

  override def cleanup(state: InternalState): Unit = {
    if (sys.props.get("protocbridge.debug") != Some("1")) {
      Files.delete(state.batFile)
    }
  }

  private def createWindowsScript(port: Int): InternalState = {
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
    InternalState(batchFile)
  }
}
