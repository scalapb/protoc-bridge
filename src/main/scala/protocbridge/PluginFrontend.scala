package protocbridge

import java.io.{InputStream, PrintWriter, StringWriter}
import java.net.ServerSocket
import java.nio.file.{Files, Path}
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}

import scala.concurrent.Future
import scala.sys.process._
import scala.collection.JavaConverters._
import scala.util.Try
import java.nio.file.attribute.PosixFilePermission
import scala.concurrent.ExecutionContext.Implicits.global


/** A PluginFrontend instance provides a platform-dependent way for protoc to communicate with
  * a JVM based ProtocCodeGenerator.
  *
  * protoc is able to launch plugins. A plugin is expected to take a serialized
  * CodeGenerationRequest via stdin and serialize a CodeGenerationRequest to stdout.
  * The idea in PluginFrontend is to create a minimal plugin that wires its stdin/stdout
  * to this JVM.
  *
  * The two-way communication always goes as follows:
  *
  * 1. protoc writes a request to the stdin of a plugin
  * 2. plugin writes the data to the channel
  * 3. this JVM reads it, interprets it as CodeGenerationRequest and process it.
  * 4. this JVM writes a CodeGenerationResponse to the channel
  * 5. this JVM closes the channel.
  * 6. the plugin reads the data and writes it to standard out.
  * 7. protoc handles the CodeGenerationResponse (creates Scala sources)
  */
trait PluginFrontend {
  type InternalState

  def prepare(plugin: ProtocCodeGenerator): (Path, InternalState)

  def cleanup(state: InternalState): Unit
}

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
      fsout.write(response.toByteArray)
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
      client.getOutputStream.write(response.toByteArray)
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

object PluginFrontend {
  private def getStackTrace(e: Throwable): String = {
    val stringWriter = new StringWriter
    val printWriter = new PrintWriter(stringWriter)
    e.printStackTrace(printWriter)
    stringWriter.toString
  }

  def runWithInputStream(plugin: ProtocCodeGenerator, fsin: InputStream): CodeGeneratorResponse = {
    val registry = ExtensionRegistry.newInstance()
    plugin.registerExtensions(registry)

    Try {
      val request = CodeGeneratorRequest.parseFrom(fsin, registry)
      plugin.run(request)
    }.recover {
      case throwable =>
        CodeGeneratorResponse.newBuilder()
          .setError(throwable.toString + "\n" + getStackTrace(throwable))
          .build
    }.get
  }

  def createTempFile(extension: String, content: String): Path = {
    val fileName = Files.createTempFile("protocbridge", extension)
    val os = Files.newOutputStream(fileName)
    os.write(content.getBytes("UTF-8"))
    os.close()
    fileName
  }

  def apply(): PluginFrontend = {
    def isWindows: Boolean = sys.props("os.name").startsWith("Windows")
    if (isWindows) new WindowsPluginFrontend("python.exe")
    else PosixPluginFrontend
  }
}
