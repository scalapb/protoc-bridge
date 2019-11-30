package protocbridge

import java.io.File
import java.nio.file.Files

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos.{
  CodeGeneratorRequest,
  CodeGeneratorResponse
}

import scala.io.Source
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

object TestJvmPlugin extends ProtocCodeGenerator {
  import collection.JavaConverters._

  override def run(in: Array[Byte]): Array[Byte] = {
    val request = CodeGeneratorRequest.parseFrom(in)

    val filesByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala
        .foldLeft[Map[String, FileDescriptor]](Map.empty) {
          case (acc, fp) =>
            val deps = fp.getDependencyList.asScala.map(acc)
            acc + (fp.getName -> FileDescriptor.buildFrom(fp, deps.toArray))
        }

    val content = (for {
      fileName <- request.getFileToGenerateList.asScala
      file = filesByName(fileName)
      msg <- file.getMessageTypes().asScala
    } yield (file.getName + ":" + msg.getFullName)).mkString("\n")

    val responseBuilder = CodeGeneratorResponse.newBuilder()
    responseBuilder
      .addFileBuilder()
      .setContent(content)
      .setName("msglist.txt")
    responseBuilder.build().toByteArray
  }
}

class ProtocIntegrationSpec extends AnyFlatSpec with Matchers {
  "ProtocBridge.run" should "invoke JVM and Java plugin properly" in {
    val protoFile =
      new File(getClass.getResource("/test.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath

    val javaOutDir = Files.createTempDirectory("javaout").toFile
    val testOutDirs = (0 to 4).map(i => Files.createTempDirectory(s"testout$i").toFile)

    ProtocBridge.run(
      args => com.github.os72.protocjar.Protoc.runProtoc(args.toArray),
      Seq(
        protocbridge.gens.java("3.8.0") -> javaOutDir,
        TestJvmPlugin -> testOutDirs(0),
        TestJvmPlugin -> testOutDirs(1),
        JvmGenerator("foo", TestJvmPlugin) -> testOutDirs(2),
        JvmGenerator("foo", TestJvmPlugin) -> testOutDirs(3),
        JvmGenerator("bar", TestJvmPlugin) -> testOutDirs(4)
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(0)

    Files.exists(javaOutDir.toPath.resolve("mytest").resolve("Test.java")) must be(
      true
    )

    testOutDirs.foreach { testOutDir =>
      val msglist = testOutDir.toPath.resolve("msglist.txt")
      Files.exists(msglist) must be(true)
      Source.fromFile(msglist.toFile).getLines().toSeq must be(
        Seq(
          "test.proto:mytest.TestMsg",
          "test.proto:mytest.AnotherMsg"
        )
      )
    }
  }
}
