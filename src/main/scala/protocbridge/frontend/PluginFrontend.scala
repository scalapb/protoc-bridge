package protocbridge.frontend

import java.io.{InputStream, PrintWriter, StringWriter}
import java.nio.file.{Files, Path}

import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import protocbridge.ProtocCodeGenerator

import scala.util.Try


/** A PluginFrontend instance provides a platform-dependent way for protoc to communicate with
  * a JVM based ProtocCodeGenerator.
  *
  * protoc is able to launch plugins. Plugins are executables that take a serialized
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

  // Notifies the frontend to set up a protoc plugin that runs the given generator. It returns
  // the system path of the executable and an arbitary internal state object that is passed
  // later. Useful for cleanup.
  def prepare(plugin: ProtocCodeGenerator): (Path, InternalState)

  def cleanup(state: InternalState): Unit
}

object PluginFrontend {
  private def getStackTrace(e: Throwable): String = {
    val stringWriter = new StringWriter
    val printWriter = new PrintWriter(stringWriter)
    e.printStackTrace(printWriter)
    stringWriter.toString
  }

  def runWithBytes(gen: ProtocCodeGenerator, bytes: Array[Byte]): CodeGeneratorResponse = {
    val registry = ExtensionRegistry.newInstance()
    gen.registerExtensions(registry)

    Try {
      val request = CodeGeneratorRequest.parseFrom(bytes, registry)
      gen.run(request)
    }.recover {
      case throwable =>
        CodeGeneratorResponse.newBuilder()
          .setError(throwable.toString + "\n" + getStackTrace(throwable))
          .build
    }.get
  }

  def runWithInputStream(gen: ProtocCodeGenerator, fsin: InputStream): CodeGeneratorResponse = {
    val bytes = org.apache.commons.io.IOUtils.toByteArray(fsin)
    runWithBytes(gen, bytes)
  }

  def createTempFile(extension: String, content: String): Path = {
    val fileName = Files.createTempFile("protocbridge", extension)
    val os = Files.newOutputStream(fileName)
    os.write(content.getBytes("UTF-8"))
    os.close()
    fileName
  }

  def newInstance(pythonExe: String = "python.exe"): PluginFrontend = {
    def isWindows: Boolean = sys.props("os.name").startsWith("Windows")
    if (isWindows) new WindowsPluginFrontend(pythonExe)
    else PosixPluginFrontend
  }
}
