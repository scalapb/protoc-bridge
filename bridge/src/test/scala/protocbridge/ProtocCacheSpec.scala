package protocbridge

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.File
import scala.concurrent.duration.Duration
import java.nio.file.Files
import scala.concurrent.Await
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.OneInstancePerTest
import scala.concurrent.Promise

class ProtocCacheSpec extends AnyFlatSpec with Matchers with OneInstancePerTest {
    val tmpDir = Files.createTempDirectory("protocache").toFile()
    val cache = new protocbridge.FileCache[String](tmpDir, downloadFile, v => v + ".exe")
    val callCount = new AtomicInteger(0)
    val p = Promise[Unit]()  // used to unsuspend the downloader

    val existing = {
        new File(tmpDir, "existing.exe.complete").createNewFile()
        val f = new File(tmpDir, "existing.exe")
        f.createNewFile()
        f
    }

    def downloadFile(v: String): Future[File] = {
        callCount.incrementAndGet()
        p.future.map {
            _ =>
                if (v == "error") throw new RuntimeException("error!")
                else {
                    val out = new File(tmpDir, v + ".tmp")
                    out.createNewFile()
                    out
                }
        }
    }

    "cache" should "immediately return an existing file and make it executable" in {
        val c = Await.result(cache.get("existing"), Duration.Inf)
        c.canExecute() must be(true)
        c must be(existing)
        callCount.get must be(0)
    }

    "cache" should "make exactly one concurrent request" in {
        val fs = (0 to 20).map(_ => cache.get("somever"))
        p.success(())
        val res = Await.result(Future.sequence(fs), Duration.Inf)
        callCount.get must be(1)
    }

    "cache" should "make exactly one concurrent request per key" in {
        val fs = (0 to 50).map(i => cache.get(s"somever${i % 3}"))
        p.success(())
        val res = Await.result(Future.sequence(fs), Duration.Inf)
        callCount.get must be(3)
        new File(tmpDir, "somever0.exe").canExecute() must be(true)
        new File(tmpDir, "somever1.exe").canExecute() must be(true)
        new File(tmpDir, "somever2.exe").canExecute() must be(true)
        new File(tmpDir, "somever3.exe").canExecute() must be(false)
        Await.result(cache.get("somever1"), Duration.Inf).canExecute() must be(true)
        callCount.get must be(3)
        Await.result(cache.get("somever3"), Duration.Inf).canExecute() must be(true)
        callCount.get must be(4)
    }

    "cache" should "make exactly one concurrent request per key on errors" in {
        val fs = (0 to 50).map(_ => cache.get(s"error"))
        p.success(())
        fs.foreach {
            f => intercept[RuntimeException](Await.result(f, Duration.Inf)).getMessage() must be("error!")
        }
        callCount.get must be(1)
        // The next call should trigger another download: misses are not remembered.
        intercept[RuntimeException](Await.result(cache.get(s"error"), Duration.Inf))
        callCount.get must be(2)
    }
}
