package protocbridge

import org.scalatest._

import java.io.File

class ProtocBridgeSpec extends FlatSpec with MustMatchers {
  val TmpPath = new File("/tmp")

  object TestFrontend extends frontend.PluginFrontend {
    type InternalState = Unit

    // Notifies the frontend to set up a protoc plugin that runs the given generator. It returns
    // the system path of the executable and an arbitary internal state object that is passed
    // later. Useful for cleanup.
    def prepare(plugin: ProtocCodeGenerator): (java.nio.file.Path, InternalState) = (null, ())

    def cleanup(state: InternalState): Unit = {}
  }

  object FoobarGen extends ProtocCodeGenerator {
    override def run(request: Array[Byte]): Array[Byte] = new Array[Byte](0)
  }

  def foobarGen(opt1: String, opt2: String): (Generator, Seq[String]) = (JvmGenerator("fff", FoobarGen), Seq(opt1, opt2))

  def run(targets: Seq[Target], params: Seq[String] = Seq.empty) =
    ProtocBridge.run(args => args, targets, params, TestFrontend)

  "run" should "pass params when there are no targets" in {
    run(Seq.empty, Seq.empty) must be (Seq.empty)
    run(Seq.empty, Seq("-x", "-y")) must be (Seq("-x", "-y"))
  }

  "run" should "allow string args for string generators" in {
    run(Seq(Target(Target.builtin("java"), TmpPath))) must be (Seq("--java_out=:/tmp"))
    run(Seq(Target(Target.builtin("java", Seq("x", "y", "z")), TmpPath))) must be (Seq("--java_out=x,y,z:/tmp"))
  }

  it should "pass builtin targets correctly" in {
    run(Seq(Target(gens.java, TmpPath))) must be (Seq("--java_out=:/tmp"))
    run(Seq(Target(gens.java, TmpPath, Seq("x", "y")))) must be (Seq("--java_out=x,y:/tmp"))
  }

  val DefineFlag="--plugin=protoc-gen-jvm_(.*?)=null".r
  val UseFlag="--jvm_(.*?)_out=:/tmp".r
  val UseFlagParams="--jvm_(.*?)_out=x,y:/tmp".r

  it should "allow using FooBarGen" in {
    run(Seq(Target(FoobarGen, TmpPath))) match {
      case Seq(DefineFlag(r), UseFlag(s)) if r == s =>
    }

    run(Seq(Target(FoobarGen, TmpPath, Seq("x", "y")))) match {
      case Seq(DefineFlag(r), UseFlagParams(s)) if r == s =>
    }
  }

  it should "allow using fooBarGen" in {
    run(Seq(Target(foobarGen("x", "y"), TmpPath))) must be (Seq(
      "--plugin=protoc-gen-fff=null", "--fff_out=x,y:/tmp"
    ))
  }
}
