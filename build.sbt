import com.typesafe.tools.mima.core._

inThisBuild(
  List(
    scalaVersion := "2.12.20",
    crossScalaVersions := Seq("2.12.20", "2.13.15", "3.3.4"),
    scalacOptions ++= List("-release", "8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

val protobufJava = "com.google.protobuf" % "protobuf-java" % "3.25.5"

val coursierVersion = "2.1.19"

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
      "org.scalatest" %% "scalatest" % "3.2.19" % "test",
      "org.scalacheck" %% "scalacheck" % "1.18.1" % "test",
      "io.get-coursier" %% "coursier" % coursierVersion % "test" cross CrossVersion.for3Use2_13
    ),
    conflictWarning := {
      if (scalaBinaryVersion.value == "3") {
        ConflictWarning("warn", Level.Warn, false)
      } else {
        conflictWarning.value
      }
    },
    Test / testOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" =>
          Nil
        case _ =>
          // TODO
          Seq(
            Tests.Exclude(
              Set(
                "protocbridge.codegen.CodeGenAppSpec",
                "protocbridge.ProtocCacheSpec"
              )
            )
          )
      }
    },
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
    conflictWarning := {
      if (scalaBinaryVersion.value == "3") {
        ConflictWarning("warn", Level.Warn, false)
      } else {
        conflictWarning.value
      }
    },
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion cross CrossVersion.for3Use2_13
    )
  )

lazy val protocGen = project
  .in(file("protoc-gen"))
  .dependsOn(bridge % "compile->compile;test->test")
  .settings(
    name := "protoc-gen",
    conflictWarning := {
      if (scalaBinaryVersion.value == "3") {
        ConflictWarning("warn", Level.Warn, false)
      } else {
        conflictWarning.value
      }
    },
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
