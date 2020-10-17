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

  def runProtoc(version: String, args: Seq[String]): Int = {
    import sys.process._

    val maybeNixDynamicLinker: Option[String] =
      sys.env.get("NIX_CC").map { nixCC =>
        Source.fromFile(nixCC + "/nix-support/dynamic-linker").mkString.trim()
      }

    ((maybeNixDynamicLinker.toSeq :+ getProtoc(version)
      .getAbsolutePath()) ++ args).!
  }

  private[this] def download(dep: Dependency): Future[File] = {
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
      if (dep.publication.classifier.value.startsWith("win")) ".ext" else ""
    s"${dep.publication.name}-${dep.publication.classifier.value}-${dep.version}$ext"
  }

  private[this] def protocDep(version: String): Dependency =
    dep"com.google.protobuf:protoc"
      .withVersion(version)
      .withPublication(
        "protoc",
        Type("jar"),
        Extension("exe"),
        Classifier(SystemDetector.detectedClassifier())
      )
}
