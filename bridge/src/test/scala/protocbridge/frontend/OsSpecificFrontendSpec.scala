package protocbridge.frontend

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.io.ByteArrayOutputStream
import scala.sys.process.ProcessIO
import scala.util.Random

class OsSpecificFrontendSpec extends AnyFlatSpec with Matchers {

  protected def testPluginFrontend(
      frontend: PluginFrontend,
      generator: ProtocCodeGenerator,
      env: ExtraEnv,
      request: Array[Byte]
  ): (frontend.InternalState, Array[Byte]) = {
    val (path, state) = frontend.prepare(
      generator,
      env
    )
    val actualOutput = new ByteArrayOutputStream()
    val process = sys.process
      .Process(path.toAbsolutePath.toString)
      .run(
        new ProcessIO(
          writeInput => {
            writeInput.write(request)
            writeInput.close()
          },
          processOutput => {
            val buffer = new Array[Byte](4096)
            var bytesRead = 0
            while (bytesRead != -1) {
              bytesRead = processOutput.read(buffer)
              if (bytesRead != -1) {
                actualOutput.write(buffer, 0, bytesRead)
              }
            }
            processOutput.close()
          },
          _.close()
        )
      )
    process.exitValue()
    frontend.cleanup(state)
    (state, actualOutput.toByteArray)
  }

  protected def testSuccess(
      frontend: PluginFrontend
  ): frontend.InternalState = {
    val random = new Random()
    val toSend = Array.fill(123)(random.nextInt(256).toByte)
    val toReceive = Array.fill(456)(random.nextInt(256).toByte)
    val env = new ExtraEnv(secondaryOutputDir = "tmp")

    val fakeGenerator = new ProtocCodeGenerator {
      override def run(request: Array[Byte]): Array[Byte] = {
        request mustBe (toSend ++ env.toByteArrayAsField)
        toReceive
      }
    }
    val (state, response) =
      testPluginFrontend(frontend, fakeGenerator, env, toSend)
    response mustBe toReceive
    state
  }

  protected def testFailure(
      frontend: PluginFrontend
  ): frontend.InternalState = {
    val random = new Random()
    val toSend = Array.fill(123)(random.nextInt(256).toByte)
    val env = new ExtraEnv(secondaryOutputDir = "tmp")

    val fakeGenerator = new ProtocCodeGenerator {
      override def run(request: Array[Byte]): Array[Byte] = {
        throw new OutOfMemoryError("test error")
      }
    }
    val (state, response) =
      testPluginFrontend(frontend, fakeGenerator, env, toSend)
    response.length must be > 0
    state
  }
}
