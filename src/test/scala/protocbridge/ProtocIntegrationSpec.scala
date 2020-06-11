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
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import protocbridge.codegen.CodeGenApp
import protocbridge.codegen.{CodeGenRequest, CodeGenResponse}

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

object TestCodeGenApp extends CodeGenApp {
  def process(request: CodeGenRequest): CodeGenResponse = {
    if (request.filesToGenerate.exists(_.getName().contains("error")))
      CodeGenResponse.fail("Error!")
    else
      CodeGenResponse.succeed(
        Seq(
          CodeGeneratorResponse.File
            .newBuilder()
            .setName("out.out")
            .setContent("out!")
            .build()
        )
      )
  }
}

object TestParameters extends CodeGenApp {

  val outputFilename = "parameters.txt"

  def process(request: CodeGenRequest): CodeGenResponse = {
    CodeGenResponse.succeed(
      Seq(
        CodeGeneratorResponse.File
          .newBuilder()
          .setName(outputFilename)
          .setContent(request.parameter)
          .build()
      )
    )
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
    val cgOutDir = Files.createTempDirectory("testout_cg").toFile()
    val paramOutDir = Files.createTempDirectory("testout_param").toFile()

    ProtocBridge.run(
      args => com.github.os72.protocjar.Protoc.runProtoc(args.toArray),
      Seq(
        protocbridge.gens.java("3.8.0") -> javaOutDir,
        TestJvmPlugin -> testOutDirs(0),
        TestJvmPlugin -> testOutDirs(1),
        JvmGenerator("foo", TestJvmPlugin) -> testOutDirs(2),
        JvmGenerator("foo", TestJvmPlugin) -> testOutDirs(3),
        JvmGenerator("bar", TestJvmPlugin) -> testOutDirs(4),
        JvmGenerator("cg", TestCodeGenApp) -> cgOutDir,
        (JvmGenerator("param", TestParameters), Seq("foo", "bar:value", "baz=qux")) -> paramOutDir
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(0)

    Files.exists(javaOutDir.toPath.resolve("mytest").resolve("Test.java")) must be(
      true
    )

    def getLines(s: Source): Seq[String] = {
      val lines = s.getLines.toList // use toList for strict evaluation
      s.close()
      lines
    }

    def fileMustBeLines(file: File, expected: Seq[String]) = {
      file.exists() must be(true)
      getLines(Source.fromFile(file)) must be(expected)
    }

    testOutDirs.foreach { testOutDir =>
      val expected =  Seq(
        "test.proto:mytest.TestMsg",
        "test.proto:mytest.AnotherMsg"
      )
      fileMustBeLines(new File(testOutDir, "msglist.txt"), expected)
    }

    fileMustBeLines(new File(cgOutDir, "out.out"), Seq("out!"))

    fileMustBeLines(new File(paramOutDir, TestParameters.outputFilename), Seq("foo,bar:value,baz=qux"))
  }

  it should "fail on plugin profile" in {
    val protoFile =
      new File(getClass.getResource("/error.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath
    val cgOutDir = Files.createTempDirectory("testout_cg").toFile()
    ProtocBridge.run(
      args => com.github.os72.protocjar.Protoc.runProtoc(args.toArray),
      Seq(
        JvmGenerator("cg", TestCodeGenApp) -> cgOutDir
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(1)
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
        ProtocBridge.run(
          args => com.github.os72.protocjar.Protoc.runProtoc(args.toArray),
          List.fill(generatorsByInvocation)(
            Target(
              JvmGenerator("foo", TestJvmPlugin),
              Files.createTempDirectory(s"foo").toFile
            )
          ),
          Seq(protoFile, "-I", protoDir)
        )
      )
    }

    Await.result(
      Future.sequence(invocations),
      Duration(60, SECONDS)
    ) must be(List.fill(parallelProtocInvocations)(0))
  }
}
