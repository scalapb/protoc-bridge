package protocbridge

/** This is the interface that code generators need to implement. */
trait ProtocCodeGenerator {
  def run(request: Array[Byte]): Array[Byte]

  def suggestedDependencies: Seq[Artifact] = Nil
}

object ProtocCodeGenerator {
  import scala.language.implicitConversions

  implicit def toGenerator(p: ProtocCodeGenerator): Generator =
    JvmGenerator("jvm", p)
}
