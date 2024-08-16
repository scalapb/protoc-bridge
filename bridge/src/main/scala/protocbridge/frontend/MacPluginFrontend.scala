package protocbridge.frontend

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}
import java.{util => ju}

/** PluginFrontend for macOS.
  *
  * Creates a server socket and uses `nc` to communicate with the socket. We use
  * a server socket instead of named pipes because named pipes are unreliable on
  * macOS: https://github.com/scalapb/protoc-bridge/issues/366. Since `nc` is
  * widely available on macOS, this is the simplest and most reliable solution
  * for macOS.
  */
object MacPluginFrontend extends SocketBasedPluginFrontend {

  protected def createShellScript(port: Int): Path = {
    val shell = sys.env.getOrElse("PROTOCBRIDGE_SHELL", "/bin/sh")
    // We use 127.0.0.1 instead of localhost for the (very unlikely) case that localhost is missing from /etc/hosts.
    val scriptName = PluginFrontend.createTempFile(
      "",
      s"""|#!$shell
          |set -e
          |nc 127.0.0.1 $port
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
