import com.typesafe.tools.mima.core._

inThisBuild(
  List(
    scalaVersion := "2.12.21",
    crossScalaVersions := Seq("2.12.21", "2.13.18", "3.3.8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

scalacOptions ++= {
  if (scalaVersion.value.startsWith("3.3.")) {
    List("-release", "11", "-Yfuture-lazy-vals")
  } else if (scalaBinaryVersion.value == "3") {
    List("-release", "17")
  } else {
    List("-release", "8")
  }
}

ThisBuild / versionScheme := Some("early-semver")

val protobufJava = "com.google.protobuf" % "protobuf-java" % "3.25.9"

val coursierVersion = Def.setting(
  if (scalaBinaryVersion.value == "2.12") {
    "2.1.24"
  } else {
    "2.1.26"
  }
)

val coursierDependencies = Def.setting {
  Seq("io.get-coursier" %% "coursier" % coursierVersion.value) ++
    (if (scalaBinaryVersion.value == "2.12") {
       // Coursier 2.1.24 is the last release published for Scala 2.12.
       // Use direct dependencies so these security fixes reach consumers' POMs.
       Seq(
         "org.apache.commons" % "commons-lang3" % "3.18.0",
         "io.airlift" % "aircompressor" % "2.0.3",
         "org.codehaus.plexus" % "plexus-utils" % "4.0.3"
       )
     } else Nil)
}

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
      "org.scalatestplus" %% "scalacheck-1-19" % "3.2.20.0" % "test",
      "org.scalatest" %% "scalatest" % "3.2.20" % "test",
      "org.scalacheck" %% "scalacheck" % "1.20.0" % "test"
    ) ++ coursierDependencies.value.map(_ % "test"),
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
    libraryDependencies ++= coursierDependencies.value
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

// Compile protocGen against the baseline runtime, then exercise its bytecode
// with a newer runtime. Changing protocGen's compile dependency would hide
// binary linkage regressions.
lazy val protocGenRuntimeTests = project
  .in(file("protoc-gen-runtime-tests"))
  .dependsOn(protocGen % "compile->compile;test->test", protocCacheCoursier)
  .settings(
    publish / skip := true,
    conflictWarning := {
      if (scalaBinaryVersion.value == "3") {
        ConflictWarning("warn", Level.Warn, false)
      } else {
        conflictWarning.value
      }
    },
    libraryDependencies += protobufJava.withRevision(
      sys.props.getOrElse("protobuf.test.version", "4.35.0")
    )
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
    protocGenRuntimeTests,
    protocCacheCoursier
  )
