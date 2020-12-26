package protocbridge

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.compiler.PluginProtos.{
  CodeGeneratorRequest,
  CodeGeneratorResponse
}

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import scala.io.Source
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import TestUtils.readLines

object TestJvmPlugin extends ProtocCodeGenerator {

  import scala.jdk.CollectionConverters._

  override def run(in: Array[Byte]): Array[Byte] = {
    val request = CodeGeneratorRequest.parseFrom(in)

    val filesByName: Map[String, FileDescriptor] =
      request.getProtoFileList.asScala
        .foldLeft[Map[String, FileDescriptor]](Map.empty) { case (acc, fp) =>
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
    responseBuilder
      .addFileBuilder()
      .setContent(
        if (request.getParameter().isEmpty()) "Empty"
        else request.getParameter()
      )
      .setName("parameters.txt")

    responseBuilder.build().toByteArray
  }
}

object TestUtils {
  def readLines(file: File) = {
    val s = Source.fromFile(file)
    try {
      Source.fromFile(file).getLines().toVector
    } finally {
      s.close()
    }
  }
}

class ProtocIntegrationSpec extends AnyFlatSpec with Matchers {
  "ProtocBridge.run" should "invoke JVM and Java plugin properly" in {
    val protoFile =
      new File(getClass.getResource("/test.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath

    val javaOutDir = Files.createTempDirectory("javaout").toFile()
    val testOutDirs =
      (0 to 4).map(i => Files.createTempDirectory(s"testout$i").toFile())

    ProtocBridge.run(
      RunProtoc,
      Seq(
        protocbridge.gens.java("3.8.0") -> javaOutDir,
        TestJvmPlugin -> testOutDirs(0),
        TestJvmPlugin -> testOutDirs(1),
        JvmGenerator("foo", TestJvmPlugin) -> testOutDirs(2),
        (
          JvmGenerator("foo", TestJvmPlugin),
          Seq("foo", "bar:value", "baz=qux")
        ) -> testOutDirs(3),
        JvmGenerator("bar", TestJvmPlugin) -> testOutDirs(4)
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(0)

    Files.exists(
      javaOutDir.toPath.resolve("mytest").resolve("Test.java")
    ) must be(
      true
    )

    testOutDirs.foreach { testOutDir =>
      val expected = Seq(
        "test.proto:mytest.TestMsg",
        "test.proto:mytest.AnotherMsg"
      )
      readLines(new File(testOutDir, "msglist.txt")) must be(expected)
    }
    readLines(new File(testOutDirs(3), "parameters.txt")) must be(
      Seq("foo,bar:value,baz=qux")
    )
    readLines(new File(testOutDirs(0), "parameters.txt")) must be(Seq("Empty"))
  }

  it should "not deadlock for highly concurrent invocations" in {
    val availableProcessors = Runtime.getRuntime.availableProcessors
    assert(
      availableProcessors > 1,
      "Several vCPUs needed for the test to be relevant"
    )

    val parallelProtocInvocations = availableProcessors * 8
    val generatorsByInvocation = availableProcessors * 8

    val protoFile =
      new File(getClass.getResource("/test.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath

    implicit val ec = ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(parallelProtocInvocations)
    )

    val invocations = List.fill(parallelProtocInvocations) {
      Future(
        blocking(
          ProtocBridge.run(
            RunProtoc,
            List.fill(generatorsByInvocation)(
              Target(
                JvmGenerator("foo", TestJvmPlugin),
                Files.createTempDirectory(s"foo").toFile
              )
            ),
            Seq(protoFile, "-I", protoDir)
          )
        )
      )
    }

    Await.result(
      Future.sequence(invocations),
      Duration(60, SECONDS)
    ) must be(List.fill(parallelProtocInvocations)(0))
  }
}
