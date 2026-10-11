package protocgen

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CodeGenResponseSpec extends AnyFlatSpec with Matchers {
  private val file = CodeGeneratorResponse.File
    .newBuilder()
    .setName("result.txt")
    .setContent("generated")
    .build()
  private val features = Set(
    CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL,
    CodeGeneratorResponse.Feature.FEATURE_SUPPORTS_EDITIONS
  )

  "CodeGenResponse" should "serialize edition bounds without losing files or features" in {
    val response = CodeGenResponse
      .succeed(Seq(file), features, 1000, 1001)
      .toCodeGeneratorResponse
    val parsed = CodeGeneratorResponse.parseFrom(response.toByteArray)

    parsed.getFileCount must be(1)
    parsed.getFile(0) must be(file)
    parsed.getSupportedFeatures must be(3L)
    for ((number, expected) <- Seq(3 -> 1000, 4 -> 1001)) {
      val field = parsed.getDescriptorForType.findFieldByNumber(number)
      if (field == null) {
        // protobuf 3.x retains the bounds as unknown fields.
        parsed.getUnknownFields.getField(number).getVarintList.size() must be(1)
        parsed.getUnknownFields.getField(number).getVarintList.get(0) must be(
          expected.toLong
        )
      } else {
        // protobuf 4.x must recognize them as the actual edition fields.
        parsed.hasField(field) must be(true)
        parsed.getField(field) must be(Int.box(expected))
      }
    }
  }

  it should "preserve both existing success overloads" in {
    val plain = CodeGenResponse.succeed(Seq(file)).toCodeGeneratorResponse
    plain.getFile(0) must be(file)
    plain.getSupportedFeatures must be(0L)

    val optional = CodeGenResponse
      .succeed(
        Seq(file),
        Set(CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL)
      )
      .toCodeGeneratorResponse
    optional.getFile(0) must be(file)
    optional.getSupportedFeatures must be(1L)
  }

  it should "preserve failure responses" in {
    val failed = CodeGeneratorResponse.parseFrom(
      CodeGenResponse
        .fail("generation failed")
        .toCodeGeneratorResponse
        .toByteArray
    )
    failed.getError must be("generation failed")
    failed.getFileCount must be(0)
    failed.hasSupportedFeatures must be(false)
    failed.getUnknownFields.asMap().isEmpty must be(true)
  }
}
