package protocbridge.frontend

import java.nio.file.{Files, Path}

import protocbridge.ProtocCodeGenerator

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.{ServerSocket, Socket}

/** A PluginFrontend that binds a server socket to a local interface. The plugin
  * is a batch script that invokes WindowsPluginFrontend.main method, in a new JVM with the same parameters
  * as the currently running JVM. The plugin will communicate its stdin and stdout to this socket.
  */
class WindowsPluginFrontend extends PluginFrontend {

  case class InternalState(batFile: Path)

  override def prepare(plugin: ProtocCodeGenerator): (Path, InternalState) = {
    val ss = new ServerSocket(0)
    val state = createWindowsScript(ss.getLocalPort)

    Future {
      val client = ss.accept()
      val response = PluginFrontend.runWithInputStream(plugin, client.getInputStream)
      client.getOutputStream.write(response)
      client.close()
      ss.close()
    }

    (state.batFile, state)
  }

  override def cleanup(state: InternalState): Unit = {
    Files.delete(state.batFile)
  }

  private def createWindowsScript(port: Int): InternalState = {
    val batchFile = PluginFrontend.createTempFile(".bat",
      s"""@echo off
          |"${sys.props("java.home")}\\bin\\java.exe" -cp "${sys.props("java.class.path")}" ${WindowsPluginFrontend.getClass.getName.stripSuffix("$")} $port
        """.stripMargin)
    InternalState(batchFile)
  }
}

object WindowsPluginFrontend {
  def main(args: Array[String]): Unit = {
    val port = args.head.toInt
    val socket = new Socket("127.0.0.1", port)
    try {
      // read stdin and write it to the socket
      val input = PluginFrontend.readInputStreamToByteArray(System.in)
      socket.getOutputStream.write(input)
      socket.shutdownOutput()
      // read the socket and write bytes to stdout
      System.out.write(PluginFrontend.readInputStreamToByteArray(socket.getInputStream))
    } finally socket.close()
  }
}