package protocbridge

import java.nio.file.Files

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorRequest, CodeGeneratorResponse}
import org.scalatest.{FlatSpec, MustMatchers}

import scala.io.Source

object TestJvmPlugin extends ProtocCodeGenerator {
  import collection.JavaConverters._

  override def run(in: Array[Byte]): Array[Byte] = {
    val request = CodeGeneratorRequest.parseFrom(in)

    val filesByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala.foldLeft[Map[String, FileDescriptor]](Map.empty) {
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
    responseBuilder.addFileBuilder()
      .setContent(content)
      .setName("msglist.txt")
    responseBuilder.build().toByteArray
  }
}

class ProtocIntegrationSpec extends FlatSpec with MustMatchers {
  "ProtocBridge.run" should  "invoke JVM and Java plugin properly" in {
    val protoFile = getClass.getResource("/test.proto").getPath
    val protoDir = getClass.getResource("/").getPath

    val javaOutDir = Files.createTempDirectory("javaout").toFile
    val testOutDir = Files.createTempDirectory("testout").toFile

    ProtocBridge.run(
      args => com.github.os72.protocjar.Protoc.runProtoc(args.toArray),
      Seq(
        protocbridge.gens.java("3.8.0") -> javaOutDir,
        TestJvmPlugin -> testOutDir
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(0)

    Files.exists(javaOutDir.toPath.resolve("mytest").resolve("Test.java")) must be(true)
    val msglist = testOutDir.toPath.resolve("msglist.txt")
    Files.exists(msglist) must be(true)
    Source.fromFile(msglist.toFile).getLines().toSeq must be (Seq(
      "test.proto:mytest.TestMsg",
      "test.proto:mytest.AnotherMsg"
    ))
  }
}
