package protocbridge

// Simple definition of Maven coordinates.
case class Artifact(
    groupId: String,
    artifactId: String,
    version: String,
    crossVersion: Boolean = false,
    configuration: Option[String] = None,
    extraAttributes: Map[String, String] = Map.empty
) {
  def this(groupId: String, artifactId: String, version: String) = {
    this(
      groupId,
      artifactId,
      version,
      crossVersion = false,
      configuration = None,
      extraAttributes = Map.empty
    )
  }

  def this(
      groupId: String,
      artifactId: String,
      version: String,
      crossVersion: Boolean
  ) = {
    this(
      groupId,
      artifactId,
      version,
      crossVersion,
      configuration = None,
      extraAttributes = Map.empty
    )
  }

  def withExtraAttributes(attrs: (String, String)*): Artifact =
    copy(extraAttributes = extraAttributes ++ attrs)

  def asSbtPlugin(scalaVersion: String, sbtVersion: String) =
    withExtraAttributes(
      "scalaVersion" -> scalaVersion,
      "sbtVersion" -> sbtVersion
    )

  override def toString =
    s"$groupId:$artifactId:$version(crossVersion=$crossVersion)"
}
