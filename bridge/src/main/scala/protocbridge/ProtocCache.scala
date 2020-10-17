package protocbridge

import scala.concurrent.Future
import dev.dirs.ProjectDirectories
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Promise
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try
import java.nio.file.Files

/** Cache for files that performs a single concurrent get per key upon miss. */
final class FileCache[K](
    cacheDir: File,
    doGet: K => Future[File],
    keyAsFileName: K => String
) {
  private[protocbridge] val tasks = new ConcurrentHashMap[K, Promise[File]]

  def get(key: K): Future[File] = {
    val f = fileForKey(key)
    val cm = completeMarker(key)
    if (cm.exists() && f.exists()) {
      f.setExecutable(true)
      Future.successful(f)
    } else {
      val p = Promise[File]()
      val prev = tasks.putIfAbsent(key, p)
      if (prev == null) {
        // we are the first
        doGet(key).map(copyToCache(_, f)).onComplete { (res: Try[File]) =>
          if (res.isFailure) { tasks.remove(key) }
          else { completeMarker(key).createNewFile() }
          p.complete(res)
        }
        p.future
      } else {
        // discard the promise
        p.complete(null)
        prev.future
      }
    }
  }

  private[protocbridge] def copyToCache(src: File, dst: File): File = {
    val dstPath = dst.toPath()
    Files.copy(src.toPath(), dst.toPath())
    dst.setExecutable(true)
    dst
  }

  private[protocbridge] def fileForKey(key: K): File =
    new File(cacheDir, keyAsFileName(key))

  private[protocbridge] def completeMarker(key: K): File =
    new File(cacheDir, keyAsFileName(key) + ".complete")
}

object FileCache {
  def cacheDir: File = {
    val dir = sys.env
      .get("PROTOC_CACHE")
      .orElse(sys.props.get("protoc.cache"))
      .map(new File(_))
      .getOrElse {
        new File(
          ProjectDirectories
            .from("com.thesamet.scalapb", "protocbridge", "protocbridge")
            .cacheDir,
          "v1"
        )
      }
    dir.mkdirs()
    dir
  }
}
