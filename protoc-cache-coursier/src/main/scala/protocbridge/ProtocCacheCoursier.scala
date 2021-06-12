package protocbridge

import scala.concurrent.Future
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.Source
import coursier._
import coursier.core.Extension

object CoursierProtocCache {
  lazy val cache: FileCache[Dependency] =
    new FileCache(FileCache.cacheDir, download, filenameFromKey)

  def getProtoc(version: String): File =
    Await.result(cache.get(protocDep(version)), Duration.Inf)

  def runProtoc(
      version: String,
      args: Seq[String],
      extraEnv: Seq[(String, String)]
  ): Int = {
    import sys.process._

    val maybeNixDynamicLinker: Option[String] =
      sys.env.get("NIX_CC").map { nixCC =>
        Source.fromFile(nixCC + "/nix-support/dynamic-linker").mkString.trim()
      }

    val cmd = (maybeNixDynamicLinker.toSeq :+ getProtoc(version)
      .getAbsolutePath()) ++ args
    Process(command = cmd, cwd = None, extraEnv: _*).!
  }

  private[this] def download(tmpDir: File, dep: Dependency): Future[File] = {
    Fetch()
      .addDependencies(dep)
      .future()
      .map(
        _.headOption.getOrElse(
          throw new RuntimeException(s"Could not find artifact for $dep")
        )
      )
  }

  private[this] def filenameFromKey(dep: Dependency) = {
    val ext =
      if (dep.publication.classifier.value.startsWith("win")) ".exe" else ""
    s"${dep.publication.name}-${dep.publication.classifier.value}-${dep.version}$ext"
  }

  private[this] def protocDep(version: String): Dependency = {
    val classifier = SystemDetector.detectedClassifier() match {
      // For M1 ARM, reuse a compatible osx-x86_64 version until binary support for
      // osx-aarch_64 is added. Workaround for:
      // https://github.com/protocolbuffers/protobuf/issues/8062
      case "osx-aarch_64" => "osx-x86_64"
      case x              => x
    }
    dep"com.google.protobuf:protoc"
      .withVersion(version)
      .withPublication(
        "protoc",
        Type("jar"),
        Extension("exe"),
        Classifier(classifier)
      )
  }

  // For backwards binary compatibility
  private def runProtoc(version: String, args: Seq[String]): Int =
    runProtoc(version, args, Seq.empty)
}
