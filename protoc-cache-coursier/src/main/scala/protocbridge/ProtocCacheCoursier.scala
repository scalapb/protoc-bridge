package protocbridge

import scala.concurrent.Future
import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration
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

    val protoc = getProtoc(version).getAbsolutePath()

    val cmd =
      (ProtocRunner.maybeNixDynamicLinker(protoc).toSeq :+ protoc) ++ args
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

  private[this] def protocDep(version: String): Dependency =
    coursier.core
      .Dependency(
        coursier.core
          .Module(
            coursier.core.Organization("com.google.protobuf"),
            coursier.core.ModuleName("protoc"),
            Map.empty
          ),
        version
      )
      .withPublication(
        "protoc",
        Type("jar"),
        Extension("exe"),
        Classifier(SystemDetector.detectedClassifier())
      )

  // For backwards binary compatibility
  private def runProtoc(version: String, args: Seq[String]): Int =
    runProtoc(version, args, Seq.empty)
}
