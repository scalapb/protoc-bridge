import com.typesafe.tools.mima.core._

inThisBuild(
  List(
    scalaVersion := "2.12.17",
    crossScalaVersions := Seq("2.12.17", "2.13.8"),
    scalacOptions ++= List("-target:jvm-1.8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

val protobufJava = "com.google.protobuf" % "protobuf-java"

val coursierVersion = "2.0.16"

lazy val bridge: Project = project
  .in(file("bridge"))
  .settings(
    name := "protoc-bridge",
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.13."))
                         Seq("-deprecation", "-Xfatal-warnings")
                       else Nil),
    libraryDependencies ++= Seq(
      "dev.dirs" % "directories" % "26",
      protobufJava % "3.19.2" % "provided",
      protobufJava % "3.19.2" % "test",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
      "org.scalatest" %% "scalatest" % "3.2.13" % "test",
      "org.scalacheck" %% "scalacheck" % "1.16.0" % "test",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.1" % "test",
      "io.get-coursier" %% "coursier" % coursierVersion % "test"
    ),
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.13."))
                         Seq("-Wconf:origin=.*JavaConverters.*:s")
                       else Nil),
    mimaPreviousArtifacts := Set(
      organization.value %% name.value % "0.9.0-RC2"
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters
        .exclude[DirectMissingMethodProblem]("protocbridge.frontend.*"),
      ProblemFilters.exclude[ReversedMissingMethodProblem](
        "protocbridge.frontend.PluginFrontend.prepare"
      )
    )
  )

lazy val protocCacheCoursier = project
  .in(file("protoc-cache-coursier"))
  .dependsOn(bridge)
  .settings(
    name := "protoc-cache-coursier",
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion
    )
  )

lazy val protocGen = project
  .in(file("protoc-gen"))
  .dependsOn(bridge % "compile->compile;test->test")
  .settings(
    name := "protoc-gen",
    libraryDependencies ++= Seq(
      protobufJava % "3.19.2" % "provided"
    ),
    mimaPreviousArtifacts := Set(
      organization.value %% name.value % "0.9.0-RC3"
    ),
    mimaBinaryIssueFilters ++= Seq(
      ProblemFilters.exclude[Problem]("protocgen.CodeGenRequest.*")
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
    protocGen,
    protocCacheCoursier
  )
