package protocbridge.frontend

import java.nio.file.{Files, Path}

import protocbridge.ProtocCodeGenerator
import java.nio.file.attribute.PosixFilePermission

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.sys.process._

/** PluginFrontend for Unix-like systems (Linux, Mac, etc)
  *
  * Creates a pair of named pipes for input/output and a shell script that communicates with them.
  */
object PosixPluginFrontend extends PluginFrontend {
  case class InternalState(inputPipe: Path, outputPipe: Path, tempDir: Path, shellScript: Path)

  override def prepare(plugin: ProtocCodeGenerator): (Path, InternalState) = {
    val tempDirPath = Files.createTempDirectory("protopipe-")
    val inputPipe = createPipe(tempDirPath, "input")
    val outputPipe = createPipe(tempDirPath, "output")
    val sh = createShellScript(inputPipe, outputPipe)

    Future {
      val fsin = Files.newInputStream(inputPipe)
      val response = PluginFrontend.runWithInputStream(plugin, fsin)
      fsin.close()

      val fsout = Files.newOutputStream(outputPipe)
      fsout.write(response)
      fsout.close()
    }
    (sh, InternalState(inputPipe, outputPipe, tempDirPath, sh))
  }

  override def cleanup(state: InternalState): Unit = {
    Files.delete(state.inputPipe)
    Files.delete(state.outputPipe)
    Files.delete(state.tempDir)
    Files.delete(state.shellScript)
  }

  private def createPipe(tempDirPath: Path, name: String): Path = {
    val pipeName = tempDirPath.resolve(name)
    Seq("mkfifo", "-m", "600", pipeName.toAbsolutePath.toString).!!
    pipeName
  }

  private def createShellScript(inputPipe: Path, outputPipe: Path): Path = {
    val scriptName = PluginFrontend.createTempFile("",
      s"""|#!/usr/bin/env sh
          |set -e
          |cat /dev/stdin > "$inputPipe"
          |cat "$outputPipe"
      """.stripMargin)
    Files.setPosixFilePermissions(scriptName, Set(
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.OWNER_READ).asJava)
    scriptName
  }
}
