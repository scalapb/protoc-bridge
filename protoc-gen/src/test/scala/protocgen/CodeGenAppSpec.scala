package protocgen

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.ProtocBridge
import java.io.File
import java.nio.file.Files
import protocbridge.JvmGenerator
import protocbridge.TestUtils.readLines
import protocbridge.RunProtoc
import protocbridge.ExtraEnv

object TestCodeGenApp extends CodeGenApp {
  def process(request: CodeGenRequest): CodeGenResponse = {
    val env = ExtraEnv.fromCodeGeneratorRequest(request.asProto)
    assert(new File(env.secondaryOutputDir).isDirectory())

    if (request.filesToGenerate.exists(_.getName().contains("error")))
      CodeGenResponse.fail("Error!")
    else
      CodeGenResponse.succeed(
        Seq(
          CodeGeneratorResponse.File
            .newBuilder()
            .setName("out.out")
            .setContent("out!")
            .build(),
          CodeGeneratorResponse.File
            .newBuilder()
            .setName("env")
            .setContent(env.secondaryOutputDir.nonEmpty.toString())
            .build()
        )
      )
  }
}

class CodeGenAppSpec extends AnyFlatSpec with Matchers {
  "protocgen.TestCodeGenApp" should "succeed by default" in {
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
    readLines(new File(cgOutDir, "env")) must be(Seq("true"))
  }

  "protocgen.TestCodeGenApp" should "fail on error.proto" in {
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
