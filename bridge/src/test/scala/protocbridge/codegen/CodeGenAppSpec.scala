package protocbridge.codegen

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.ProtocBridge
import java.io.File
import java.nio.file.Files
import protocbridge.JvmGenerator
import protocbridge.TestUtils.readLines
import scala.annotation.nowarn
import protocbridge.RunProtoc

@nowarn("msg=(trait|class|object) CodeGen.*is deprecated")
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

class CodeGenAppSpec extends AnyFlatSpec with Matchers {
  "protocbridge.TestCodeGenApp" should "succeed by default" in {
    val protoFile =
      new File(getClass.getResource("/test.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath
    val cgOutDir = Files.createTempDirectory("testout_cg").toFile()
    ProtocBridge.run(
      RunProtoc,
      Seq(
        JvmGenerator("cg", TestCodeGenApp) -> cgOutDir
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(0)
    readLines(new File(cgOutDir, "out.out")) must be(Seq("out!"))
  }

  it should "fail on error.proto" in {
    val protoFile =
      new File(getClass.getResource("/error.proto").getFile).getAbsolutePath
    val protoDir = new File(getClass.getResource("/").getFile).getAbsolutePath
    val cgOutDir = Files.createTempDirectory("testout_cg").toFile()
    ProtocBridge.run(
      RunProtoc,
      Seq(
        JvmGenerator("cg", TestCodeGenApp) -> cgOutDir
      ),
      Seq(protoFile, "-I", protoDir)
    ) must be(1)
  }
}
