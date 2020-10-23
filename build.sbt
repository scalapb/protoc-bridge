inThisBuild(
  List(
    scalaVersion := "2.12.10",
    crossScalaVersions := Seq("2.12.10", "2.13.2"),
    scalacOptions ++= List("-target:jvm-1.8"),
    javacOptions ++= List("-target", "8", "-source", "8"),
    organization := "com.thesamet.scalapb"
  )
)

val protobufJava = "com.google.protobuf" % "protobuf-java"

val coursierVersion = "2.0.5"

lazy val bridge: Project = project
  .in(file("bridge"))
  .settings(
    name := "protoc-bridge",
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.13."))
                         Seq("-deprecation", "-Xfatal-warnings")
                       else Nil),
    libraryDependencies ++= Seq(
      "dev.dirs" % "directories" % "21",
      protobufJava % "3.7.1" % "provided",
      protobufJava % "3.7.1" % "test",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % "test",
      "org.scalatest" %% "scalatest" % "3.2.2" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.2.0" % "test",
      "io.get-coursier" %% "coursier" % coursierVersion % "test"
    ),
    mimaPreviousArtifacts := Set(
      organization.value %% name.value % "0.9.0-RC2"
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
      protobufJava % "3.12.2" % "provided"
    ),
    mimaPreviousArtifacts := Set(
      organization.value %% name.value % "0.9.0-RC3"
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
