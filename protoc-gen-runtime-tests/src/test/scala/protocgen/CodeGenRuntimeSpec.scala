package protocgen

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import com.google.protobuf.DescriptorProtos.Edition
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import protocbridge.{
  CoursierProtocCache,
  JvmGenerator,
  ProtocBridge,
  ProtocRunner
}

// Run the same regression tests using protocGen compiled against protobuf 3.x.
class CodeGenRuntimeSpec extends CodeGenResponseSpec {
  it should "generate an edition 2023 file through protoc" in {
    val directory = Files.createTempDirectory("edition-codegen")
    val proto = directory.resolve("edition.proto")
    val output = directory.resolve("result.txt")
    Files.write(
      proto,
      """edition = "2023";
        |package editiontest;
        |message Example { string value = 1; }
        |""".stripMargin.getBytes(StandardCharsets.UTF_8)
    )
    val generator = new CodeGenApp {
      def process(request: CodeGenRequest): CodeGenResponse = {
        val descriptor = request.filesToGenerate.head
        CodeGenResponse.succeed(
          Seq(
            CodeGeneratorResponse.File
              .newBuilder()
              .setName("result.txt")
              .setContent(descriptor.getMessageTypes.get(0).getFullName)
              .build()
          ),
          Set(CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS),
          Edition.EDITION_2023.getNumber,
          Edition.EDITION_2023.getNumber
        )
      }
    }
    val runner = new ProtocRunner[Int] {
      def run(args: Seq[String], extraEnv: Seq[(String, String)]): Int =
        CoursierProtocCache.runProtoc("4.28.2", args, extraEnv)
    }
    try {
      ProtocBridge.execute(
        runner,
        Seq(JvmGenerator("editiontest", generator) -> directory.toFile),
        Seq(proto.toString, "-I", directory.toString),
        _ => throw new AssertionError("Unexpected artifact resolution")
      ) must be(0)
      new String(Files.readAllBytes(output), StandardCharsets.UTF_8) must be(
        "editiontest.Example"
      )
    } finally {
      Files.deleteIfExists(output)
      Files.deleteIfExists(proto)
      Files.deleteIfExists(directory)
    }
  }
}
