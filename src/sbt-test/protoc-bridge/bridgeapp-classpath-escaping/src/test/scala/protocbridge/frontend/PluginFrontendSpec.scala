package protocbridge.frontend

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import org.scalatest.{FlatSpec, MustMatchers}
import protocbridge.ProtocCodeGenerator

import scala.collection.JavaConverters._

class PluginFrontendSpec extends FlatSpec with MustMatchers {
    it must "successfully execute a program" in {
      val frontendInstance = PluginFrontend.newInstance

      val generatorStub = new ProtocCodeGenerator {
        override def run(request: Array[Byte]): Array[Byte] = Array.empty
      }

      val (path, state) = frontendInstance.prepare(generatorStub)

      println(path)
      java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8).asScala.foreach(println)

      val process = sys.process.Process(path.toAbsolutePath.toString)
        .#<(new ByteArrayInputStream(Array.empty))
        .run()
      process.exitValue() mustBe 0
      frontendInstance.cleanup(state)
    }
}
