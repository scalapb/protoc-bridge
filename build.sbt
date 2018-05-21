import ReleaseTransformations._

scalaVersion in ThisBuild := "2.11.7"

crossScalaVersions in ThisBuild := Seq("2.10.7", "2.11.12", "2.12.4")

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

releasePublishArtifactsAction := PgpKeys.publishSigned.value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = "publishSigned" :: _, enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  pushChanges,
  ReleaseStep(action = "sonatypeReleaseAll" :: _, enableCrossBuild = true)
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.5.1" % "test"
)
