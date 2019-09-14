import ReleaseTransformations._

scalaVersion in ThisBuild := "2.12.10"

crossScalaVersions in ThisBuild := Seq("2.10.7", "2.11.12", "2.12.10", "2.13.0")

scalacOptions in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target:jvm-1.7")
    case _ => Nil
  }
}

javacOptions in ThisBuild ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 11 => List("-target", "7", "-source", "7")
    case _ => Nil
  }
}

organization in ThisBuild := "com.thesamet.scalapb"

name in ThisBuild := "protoc-bridge"

releaseCrossBuild := true

publishTo := sonatypePublishTo.value

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges,
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.8.0" % "test",
  "com.github.os72" % "protoc-jar" % "3.8.0" % "test"
)
