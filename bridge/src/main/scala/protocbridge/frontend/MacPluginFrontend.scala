package protocbridge.frontend

import org.newsclub.net.unix.AFUNIXServerSocket
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.net.ServerSocket
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}
import java.{util => ju}

/** PluginFrontend for macOS.
  *
  * Creates a server socket and uses `nc` to communicate with the socket. We use
  * a server socket instead of named pipes because named pipes are unreliable on
  * macOS: https://github.com/scalapb/protoc-bridge/issues/366
  *
  * Since `nc` is widely available on macOS, this is the simplest alternative
  * for macOS. However, raw `nc` is also not very reliable on macOS:
  * https://github.com/scalapb/protoc-bridge/issues/379
  *
  * The most reliable way to communicate is found to be with a domain socket and
  * a server-side read timeout, which are implemented here.
  */
object MacPluginFrontend extends SocketBasedPluginFrontend {
  case class InternalState(
      shellScript: Path,
      tempDirPath: Path,
      socketPath: Path,
      serverSocket: ServerSocket
  )

  override def prepare(
      plugin: ProtocCodeGenerator,
      env: ExtraEnv
  ): (Path, InternalState) = {
    val tempDirPath = Files.createTempDirectory("protocbridge")
    val socketPath = tempDirPath.resolve("socket")
    val serverSocket = AFUNIXServerSocket.bindOn(socketPath, true)
    val sh = createShellScript(socketPath)

    runWithSocket(plugin, env, serverSocket)

    (sh, InternalState(sh, tempDirPath, socketPath, serverSocket))
  }

  override def cleanup(state: InternalState): Unit = {
    state.serverSocket.close()
    if (sys.props.get("protocbridge.debug") != Some("1")) {
      Files.delete(state.tempDirPath)
      Files.delete(state.shellScript)
    }
  }

  private def createShellScript(socketPath: Path): Path = {
    val shell = sys.env.getOrElse("PROTOCBRIDGE_SHELL", "/bin/sh")
    val scriptName = PluginFrontend.createTempFile(
      "",
      s"""|#!$shell
          |set -e
          |nc -U "$socketPath"
      """.stripMargin
    )
    val perms = new ju.HashSet[PosixFilePermission]
    perms.add(PosixFilePermission.OWNER_EXECUTE)
    perms.add(PosixFilePermission.OWNER_READ)
    Files.setPosixFilePermissions(
      scriptName,
      perms
    )
    scriptName
  }
}
