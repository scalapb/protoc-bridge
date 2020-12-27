package protocbridge

import org.scalatest._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.google.protobuf.UnknownFieldSet
import com.google.protobuf.ByteString

class ExtraEnvSpec extends AnyFlatSpec with Matchers {
  "ExtraEnv" should "parse and serialize" in {
    val env = new ExtraEnv(secondaryOutputDir = "foo")
    val bs = ByteString.copyFrom(env.toByteArrayAsField)
    val requestWithEnv = CodeGeneratorRequest.parseFrom(env.toByteArrayAsField)
    ExtraEnvParser
      .fromCodeGeneratorRequest(requestWithEnv)
      .secondaryOutputDir must be(env.secondaryOutputDir)

  }
}
