import ReleaseTransformations._

scalaVersion in ThisBuild := "2.12.10"

crossScalaVersions in ThisBuild := Seq("2.12.10", "2.13.1")

scalacOptions in ThisBuild ++= List("-target:jvm-1.8")

javacOptions in ThisBuild ++= List("-target", "8", "-source", "8")

organization in ThisBuild := "com.thesamet.scalapb"

name in ThisBuild := "protoc-bridge"

releaseCrossBuild := true

publishTo := sonatypePublishToBundle.value

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
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)

libraryDependencies ++= Seq(
  "com.google.protobuf" % "protobuf-java" % "3.7.1",
  "org.scalatestplus" %% "scalacheck-1-14" % "3.1.1.1",
  "org.scalatest" %% "scalatest" % "3.1.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  "com.github.os72" % "protoc-jar" % "3.11.4" % "test"
)
