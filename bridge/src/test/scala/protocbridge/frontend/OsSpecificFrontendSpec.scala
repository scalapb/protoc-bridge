package protocbridge.frontend

import org.apache.commons.io.IOUtils
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.io.ByteArrayOutputStream
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, TimeoutException}
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
            IOUtils.copy(processOutput, actualOutput)
            processOutput.close()
          },
          processError => {
            IOUtils.copy(processError, System.err)
            processError.close()
          }
        )
      )
    try {
      Await.result(Future { process.exitValue() }, 5.seconds)
    } catch {
      case _: TimeoutException =>
        System.err.println(s"Timeout")
        process.destroy()
    }
    frontend.cleanup(state)
    (state, actualOutput.toByteArray)
  }

  protected def testSuccess(
      frontend: PluginFrontend
  ): frontend.InternalState = {
    val random = new Random()
    val toSend = Array.fill(100000)(random.nextInt(256).toByte)
    val toReceive = Array.fill(100000)(random.nextInt(256).toByte)
    val env = new ExtraEnv(secondaryOutputDir = "tmp")

    val fakeGenerator = new ProtocCodeGenerator {
      override def run(request: Array[Byte]): Array[Byte] = {
        request mustBe (toSend ++ env.toByteArrayAsField)
        toReceive
      }
    }
    // Repeat 100,000 times since named pipes on macOS are flaky.
    val repeatCount = 100000
    for (i <- 1 until repeatCount) {
      if (i % 100 == 1) println(s"Running iteration $i of $repeatCount")
      val (state, response) =
        testPluginFrontend(frontend, fakeGenerator, env, toSend)
      if (!(response sameElements toReceive)) {
        System.err.println(
          s"Failed on iteration $i of $repeatCount ($state): ${response.length} != ${toReceive.length}"
        )
      }
    }
    val (state, response) =
      testPluginFrontend(frontend, fakeGenerator, env, toSend)
    if (!(response sameElements toReceive)) {
      System.err.println(
        s"Failed on iteration $repeatCount of $repeatCount ($state): ${response.length} != ${toReceive.length}"
      )
    }
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
