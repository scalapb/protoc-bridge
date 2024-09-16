import com.typesafe.tools.mima.core._

inThisBuild(
  List(
    scalaVersion := "2.12.18",
    crossScalaVersions := Seq("2.12.18", "2.13.12"),
    scalacOptions ++= List("-release", "8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

val protobufJava = "com.google.protobuf" % "protobuf-java" % "3.24.4"

val coursierVersion = "2.1.8"

lazy val bridge: Project = project
  .in(file("bridge"))
  .settings(
    name := "protoc-bridge",
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.13."))
                         Seq("-deprecation", "-Xfatal-warnings")
                       else Nil),
    libraryDependencies ++= Seq(
      "dev.dirs" % "directories" % "26",
      protobufJava % "provided",
      protobufJava % "test",
      "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % "test",
      "org.scalatest" %% "scalatest" % "3.2.17" % "test",
      "org.scalacheck" %% "scalacheck" % "1.17.0" % "test",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0" % "test",
      "io.get-coursier" %% "coursier" % coursierVersion % "test",
      "com.kohlschutter.junixsocket" % "junixsocket-core" % "2.10.0"
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
      protobufJava % "provided"
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
