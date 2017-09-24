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
  case class InternalState(inputPipe: Path, outputPipe: Path, shellScript: Path)

  override def prepare(plugin: ProtocCodeGenerator): (Path, InternalState) = {
    val inputPipe = createPipe()
    val outputPipe = createPipe()
    val sh = createShellScript(inputPipe, outputPipe)

    Future {
      val fsin = Files.newInputStream(inputPipe)
      val response = PluginFrontend.runWithInputStream(plugin, fsin)
      fsin.close()

      val fsout = Files.newOutputStream(outputPipe)
      fsout.write(response)
      fsout.close()
    }
    (sh, InternalState(inputPipe, outputPipe, sh))
  }

  override def cleanup(state: InternalState): Unit = {
    Files.delete(state.inputPipe)
    Files.delete(state.outputPipe)
    Files.delete(state.shellScript)
  }

  private def createPipe(): Path = {
    val pipeName = Files.createTempFile("protopipe-", ".pipe")
    Files.delete(pipeName)
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
