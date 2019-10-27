package protocbridge

import org.scalatest._
import java.io.File
import java.util.regex.Pattern

class ProtocBridgeSpec extends FlatSpec with MustMatchers {
  val TmpPath = new File("/tmp").getAbsoluteFile
  val TmpPath1 = new File("/tmp/x").getAbsoluteFile
  val TmpPath2 = new File("/tmp/y").getAbsoluteFile

  object TestFrontend extends frontend.PluginFrontend {
    type InternalState = Unit

    // Notifies the frontend to set up a protoc plugin that runs the given generator. It returns
    // the system path of the executable and an arbitary internal state object that is passed
    // later. Useful for cleanup.
    def prepare(
        plugin: ProtocCodeGenerator
    ): (java.nio.file.Path, InternalState) = (null, ())

    def cleanup(state: InternalState): Unit = {}
  }

  object FoobarGen extends ProtocCodeGenerator {
    override def run(request: Array[Byte]): Array[Byte] = new Array[Byte](0)
  }

  def foobarGen(opt1: String, opt2: String): (Generator, Seq[String]) =
    (JvmGenerator("fff", FoobarGen), Seq(opt1, opt2))

  def run(targets: Seq[Target], params: Seq[String] = Seq.empty) =
    ProtocBridge.run(args => args, targets, params, TestFrontend)

  "run" should "pass params when there are no targets" in {
    run(Seq.empty, Seq.empty) must be(Seq.empty)
    run(Seq.empty, Seq("-x", "-y")) must be(Seq("-x", "-y"))
  }

  "run" should "allow string args for string generators" in {
    run(Seq(Target(Target.builtin("java"), TmpPath))) must be(
      Seq(s"--java_out=:$TmpPath")
    )
    run(Seq(Target(Target.builtin("java", Seq("x", "y", "z")), TmpPath))) must be(
      Seq(s"--java_out=x,y,z:$TmpPath")
    )
  }

  it should "pass builtin targets correctly" in {
    run(Seq(Target(gens.java, TmpPath))) must be(Seq(s"--java_out=:$TmpPath"))
    run(Seq(Target(gens.java, TmpPath, Seq("x", "y")))) must be(
      Seq(s"--java_out=x,y:$TmpPath")
    )
  }

  it should "pass external plugins correctly" in {
    run(Seq(Target(gens.plugin("foo"), TmpPath))) must be(
      Seq(s"--foo_out=:$TmpPath")
    )
    run(
      Seq(
        Target(gens.plugin("foo"), TmpPath),
        Target(gens.plugin("bar"), TmpPath2)
      )
    ) must be(Seq(s"--foo_out=:$TmpPath", s"--bar_out=:$TmpPath2"))

    run(
      Seq(
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath),
        Target(gens.plugin("bar"), TmpPath2)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-foo=/path/to/plugin",
        s"--foo_out=:$TmpPath",
        s"--bar_out=:$TmpPath2"
      )
    )

    run(
      Seq(
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath),
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath1),
        Target(gens.plugin("foo"), TmpPath2),
        Target(gens.plugin("bar"), TmpPath)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-foo=/path/to/plugin",
        s"--foo_out=:$TmpPath",
        s"--foo_out=:$TmpPath1",
        s"--foo_out=:$TmpPath2",
        s"--bar_out=:$TmpPath"
      )
    )
  }

  it should "not allow ambigious paths for plugins" in {
    intercept[RuntimeException] {
      run(
        Seq(
          Target(gens.plugin("foo", "/path/to/plugin"), TmpPath1),
          Target(gens.plugin("foo", "/other/path/to/plugin"), TmpPath1)
        )
      )
    }.getMessage() must be("Different paths found for the plugin: foo")
  }

  val DefineFlag = "--plugin=protoc-gen-jvm_(.*?)=null".r
  val UseFlag = s"--jvm_(.*?)_out=:${Pattern.quote(TmpPath.toString)}".r
  val UseFlagParams =
    s"--jvm_(.*?)_out=x,y:${Pattern.quote(TmpPath.toString)}".r

  it should "allow using FooBarGen" in {
    run(Seq(Target(FoobarGen, TmpPath))) match {
      case Seq(DefineFlag(r), UseFlag(s)) if r == s =>
    }

    run(Seq(Target(FoobarGen, TmpPath, Seq("x", "y")))) match {
      case Seq(DefineFlag(r), UseFlagParams(s)) if r == s =>
    }
  }

  it should "allow using fooBarGen multiple times" in {
    run(
      Seq(
        Target(foobarGen("x", "y"), TmpPath),
        FoobarGen -> TmpPath1,
        Target(foobarGen("foo", "bar"), TmpPath2)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-fff_0=null",
        "--plugin=protoc-gen-jvm_1=null",
        "--plugin=protoc-gen-fff_2=null",
        s"--fff_0_out=x,y:$TmpPath",
        s"--jvm_1_out=:$TmpPath1",
        s"--fff_2_out=foo,bar:$TmpPath2"
      )
    )
  }
}
