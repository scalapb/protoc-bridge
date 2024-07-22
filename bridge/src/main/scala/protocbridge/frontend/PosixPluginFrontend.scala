package protocbridge.frontend

import java.nio.file.{Files, Path}

import protocbridge.ProtocCodeGenerator
import protocbridge.ExtraEnv
import java.nio.file.attribute.PosixFilePermission

import scala.concurrent.blocking
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import java.{util => ju}

/** PluginFrontend for Unix-like systems (Linux, Mac, etc)
  *
  * Creates a pair of named pipes for input/output and a shell script that
  * communicates with them.
  */
object PosixPluginFrontend extends PluginFrontend {
  case class InternalState(
      inputPipe: Path,
      outputPipe: Path,
      tempDir: Path,
      shellScript: Path
  )

  override def prepare(
      plugin: ProtocCodeGenerator,
      env: ExtraEnv
  ): (Path, InternalState) = {
    val tempDirPath = Files.createTempDirectory("protopipe-")
    val inputPipe = createPipe(tempDirPath, "input")
    val outputPipe = createPipe(tempDirPath, "output")
    val sh = createShellScript(inputPipe, outputPipe)

    Future {
      blocking {
        System.err.println("Files.newInputStream...")
        val fsin = Files.newInputStream(inputPipe)
        System.err.println("PluginFrontend.runWithInputStream...")
        val response = PluginFrontend.runWithInputStream(plugin, fsin, env)
        System.err.println("fsin.close...")
        fsin.close()

        System.err.println("Files.newOutputStream...")
        val fsout = Files.newOutputStream(outputPipe)
        System.err.println("fsout.write...")
        fsout.write(response)
        System.err.println("fsout.close...")
        fsout.close()

        System.err.println("blocking done.")
      }
    }
    (sh, InternalState(inputPipe, outputPipe, tempDirPath, sh))
  }

  override def cleanup(state: InternalState): Unit = {
    if (sys.props.get("protocbridge.debug") != Some("1")) {
      Files.delete(state.inputPipe)
      Files.delete(state.outputPipe)
      Files.delete(state.tempDir)
      Files.delete(state.shellScript)
    }
  }

  private def createPipe(tempDirPath: Path, name: String): Path = {
    val pipeName = tempDirPath.resolve(name)
    Seq("mkfifo", "-m", "600", pipeName.toAbsolutePath.toString).!!
    pipeName
  }

  private def createShellScript(inputPipe: Path, outputPipe: Path): Path = {
    val shell = sys.env.getOrElse("PROTOCBRIDGE_SHELL", "/bin/sh")
    val scriptName = PluginFrontend.createTempFile(
      "",
      s"""|#!$shell
          |set -e
          |cat /dev/stdin > "$inputPipe"
          |cat "$outputPipe"
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
