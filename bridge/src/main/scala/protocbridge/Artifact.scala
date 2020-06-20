package protocbridge

// Simple definition of Maven coordinates.
case class Artifact(
    groupId: String,
    artifactId: String,
    version: String,
    crossVersion: Boolean = false
) {
  override def toString =
    s"$groupId:$artifactId:$version(crossVersion=$crossVersion)"
}
