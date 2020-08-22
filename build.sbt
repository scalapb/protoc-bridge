import ReleaseTransformations._

inThisBuild(
  List(
    scalaVersion := "2.12.10",
    crossScalaVersions := Seq("2.12.10", "2.13.2"),
    scalacOptions ++= List("-target:jvm-1.8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

publishTo in ThisBuild := sonatypePublishToBundle.value

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
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommand("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

val protobufJava = "com.google.protobuf" % "protobuf-java"

lazy val bridge = project
  .in(file("bridge"))
  .settings(
    name := "protoc-bridge",
    mimaPreviousArtifacts := Set(organization.value %% name.value % "0.9.0-RC1"),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.13."))
                         Seq("-deprecation", "-Xfatal-warnings")
                       else Nil),
    libraryDependencies ++= Seq(
      protobufJava % "3.7.1" % "provided",
      protobufJava % "3.7.1" % "test",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
      "org.scalatest" %% "scalatest" % "3.2.2" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
      "com.github.os72" % "protoc-jar" % "3.11.4" % "test",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6" % "test"
    )
  )

lazy val protocGen = project
  .in(file("protoc-gen"))
  .dependsOn(bridge % "compile->compile;test->test")
  .settings(
    name := "protoc-gen",
    libraryDependencies ++= Seq(
      protobufJava % "3.12.2" % "provided"
    ),
    Test / unmanagedResourceDirectories ++= (bridge / Test / unmanagedResourceDirectories).value
  )

lazy val root = project
  .in(file("."))
  .settings(
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  )
  .aggregate(
    bridge,
    protocGen
  )
