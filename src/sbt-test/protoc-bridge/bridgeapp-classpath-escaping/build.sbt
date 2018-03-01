
lazy val bridgeVersion = sys.props.get("protoc.bridge.version") match {
  case Some(x) => x
  case None => sys.error("""|The system property 'protoc.bridge.version' is not defined.
                            |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

lazy val parentScalaVersion = sys.props.get("protoc.bridge.scala.version") match {
  case Some(x) => x
  case None => sys.error("""|The system property 'protoc.bridge.scala.version' is not defined.
                            |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

scalaVersion := parentScalaVersion

lazy val bridgeModule = "com.thesamet.scalapb" %% "protoc-bridge" % bridgeVersion

libraryDependencies ++= Seq(
  bridgeModule,
  "org.scalatest" %% "scalatest" % "3.0.4" % "test"
)

// Ensure that protoc-bridge JAR path includes percent-encoded characters
(fullClasspath in Test) := {
  (fullClasspath in Test).value.map { af =>
    af.get(moduleID.key).fold(af) { module =>
      if (module.organization == bridgeModule.organization &&
        (module.name == bridgeModule.name || module.name == s"${bridgeModule.name}_${scalaBinaryVersion.value}")) {
        // This is a valid file name with RFC 3986 reserved characters valid in classpath
        val testPrefix: String = """temp%!'()@&=+$,#[]"""
        val targetPath = java.nio.file.Files.createTempFile(testPrefix, ".jar")

        println(s"Replacing source file for $module from '${af.data.toString}' to '$targetPath'")
        java.nio.file.Files.copy(af.data.toPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val targetFile = targetPath.toFile
        targetFile.deleteOnExit()
        af.copy(targetFile)(af.metadata)
      } else {
        af
      }
    }
  }
}

