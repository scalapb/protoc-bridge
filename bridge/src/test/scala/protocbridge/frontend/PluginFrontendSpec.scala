package protocbridge.frontend

import java.io.ByteArrayInputStream

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import protocbridge.ExtraEnv

class PluginFrontendSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {
  def expected(error: String) =
    CodeGeneratorResponse.newBuilder().setError(error).build()

  def actual(error: String) =
    CodeGeneratorResponse.parseFrom(
      PluginFrontend.createCodeGeneratorResponseWithError(error)
    )

  "createCodeGeneratorResponseWithError" should "create valid objects" in {
    actual("") must be(expected(""))
    actual("foo") must be(expected("foo"))
    actual("\u2035") must be(expected("\u2035"))
    actual("a" * 128) must be(expected("a" * 128))
    actual("a" * 256) must be(expected("a" * 256))
    actual("\u3714\u3715" * 256) must be(expected("\u3714\u3715" * 256))
    actual("abc" * 1000) must be(expected("abc" * 1000))
    forAll(MinSuccessful(1000)) { (s: String) =>
      actual(s) must be(expected(s))
    }

  }

  "readInputStreamToByteArray" should "read the input stream to a byte array" in {
    val env = new ExtraEnv("foo")
    def readInput(bs: Array[Byte]) =
      PluginFrontend.readInputStreamToByteArrayWithEnv(
        new ByteArrayInputStream(bs),
        env
      )

    readInput(Array.empty) must be(env.toByteArrayAsField)
    readInput(Array[Byte](1, 2, 3, 4)) must be(
      Array(1, 2, 3, 4) ++ env.toByteArrayAsField
    )
    val special = Array.tabulate[Byte](10000) { n =>
      (n % 37).toByte
    }
    readInput(special) must be(special ++ env.toByteArrayAsField)
  }
}
