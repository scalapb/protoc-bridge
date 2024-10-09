package protocbridge.frontend

import org.apache.commons.io.IOUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import protocbridge.{ExtraEnv, ProtocCodeGenerator}

import java.io.ByteArrayOutputStream
import scala.sys.process.ProcessIO
import scala.util.Random

class PosixPluginFrontendSpec extends AnyFlatSpec with Matchers {
  if (!PluginFrontend.isWindows) {
    it must "execute a program that forwards input and output to given stream" in {
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

      // Repeat 10,000 times since named pipes on macOS are flaky.
      val repeatCount = 10000
      for (i <- 1 to repeatCount) {
        if (i % 100 == 1) println(s"Running iteration $i of $repeatCount at ${java.time.LocalDateTime.now}")

        val (path, state) = PosixPluginFrontend.prepare(
          fakeGenerator,
          env
        )
        val actualOutput = new ByteArrayOutputStream()
        val process = sys.process
          .Process(path.toAbsolutePath.toString)
          .run(new ProcessIO(writeInput => {
            writeInput.write(toSend)
            writeInput.close()
          }, processOutput => {
            IOUtils.copy(processOutput, actualOutput)
            processOutput.close()
          }, _.close()))
        process.exitValue()
        actualOutput.toByteArray mustBe toReceive
        PosixPluginFrontend.cleanup(state)
      }
    }
  }
}
