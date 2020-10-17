package protocbridge

object RunProtoc {
  def run(args: Seq[String]): Unit = {
    com.github.os72.protocjar.Protoc.runProtoc(args.toArray)
  }
}
