package protocbridge.frontend

import java.nio.file.{Files, Path}
import protocbridge.ProtocCodeGenerator
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.ServerSocket

/** A PluginFrontend that binds a server socket to a local interface. The plugin
  * is a batch script that invokes Python, which will communicate its stdin and stdout
  * to this socket.
  */
class WindowsPluginFrontend(pythonExecutable: String) extends PluginFrontend {

  case class InternalState(batFile: Path, pyFile: Path)

  override def prepare(plugin: ProtocCodeGenerator): (Path, InternalState) = {
    val ss = new ServerSocket(0)
    val state = createWindowsScripts(ss.getLocalPort)

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
    Files.delete(state.pyFile)
  }

  private def createWindowsScripts(port: Int): InternalState = {
    val pythonScript = PluginFrontend.createTempFile(".py",
      s"""|import sys, socket
          |
          |content = sys.stdin.read()
          |s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
          |s.connect(('127.0.0.1', int(sys.argv[1])))
          |s.sendall(content)
          |s.shutdown(socket.SHUT_WR)
          |while 1:
          |    data = s.recv(1024)
          |    if data == '':
          |        break
          |    sys.stdout.write(data)
          |s.close()
      """.stripMargin)

    val batchFile = PluginFrontend.createTempFile(".bat",
      s"""@echo off
          |$pythonExecutable -u $pythonScript $port
        """.stripMargin)
    InternalState(batchFile, pythonScript)
  }
}
