package protocbridge

import org.scalatest._
import java.io.File
import java.util.regex.Pattern
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

object FoobarGen extends ProtocCodeGenerator {
  override def run(request: Array[Byte]): Array[Byte] = new Array[Byte](0)
}

class ProtocBridgeSpec extends AnyFlatSpec with Matchers {
  val TmpPath = new File("/tmp").getAbsoluteFile
  val TmpPath1 = new File("/tmp/x").getAbsoluteFile
  val TmpPath2 = new File("/tmp/y").getAbsoluteFile

  object TestFrontend extends frontend.PluginFrontend {
    type InternalState = Unit

    // Notifies the frontend to set up a protoc plugin that runs the given generator. It returns
    // the system path of the executable and an arbitary internal state object that is passed
    // later. Useful for cleanup.
    def prepare(
        plugin: ProtocCodeGenerator,
        env: ExtraEnv
    ): (java.nio.file.Path, InternalState) = (null, ())

    def cleanup(state: InternalState): Unit = {}
  }

  def foobarGen(opt1: String, opt2: String): (Generator, Seq[String]) =
    (JvmGenerator("fff", FoobarGen), Seq(opt1, opt2))

  def sandboxedGen(opts: String*): (Generator, Seq[String]) =
    (
      SandboxedJvmGenerator.forModule(
        "sandboxed",
        Artifact("group", "id", "version"),
        "protocbridge.FoobarGen$",
        Nil
      ),
      opts
    )

  def run(targets: Seq[Target], params: Seq[String] = Seq.empty) =
    ProtocBridge.execute(
      ProtocRunner(args => args),
      targets,
      params,
      TestFrontend,
      _ => getClass.getClassLoader
    )

  "run" should "pass params when there are no targets" in {
    run(Seq.empty, Seq.empty) must be(Seq.empty)
    run(Seq.empty, Seq("-x", "-y")) must be(Seq("-x", "-y"))
  }

  "run" should "allow string args for string generators" in {
    run(Seq(Target(Target.builtin("java"), TmpPath))) must be(
      Seq(s"--java_out=$TmpPath")
    )
    run(
      Seq(Target(Target.builtin("java", Seq("x", "y", "z")), TmpPath))
    ) must be(
      Seq(s"--java_out=$TmpPath", "--java_opt=x,y,z")
    )
  }

  it should "pass builtin targets correctly" in {
    run(Seq(Target(gens.java, TmpPath))) must be(Seq(s"--java_out=$TmpPath"))
    run(Seq(Target(gens.java, TmpPath, Seq("x", "y")))) must be(
      Seq(s"--java_out=$TmpPath", "--java_opt=x,y")
    )
  }

  it should "pass external plugins correctly" in {

    // ensure no suffix is added if we have only one native, path-less generator with the same name to maintain backward
    // compatibility with <= 0.9.1 clients that assume that this native generator passed once would not be suffixed,
    // and therefore could declare that plugin path with the exact name used by the generator passed in the target
    // https://github.com/thesamet/sbt-protoc/blob/v1.0.0/src/main/scala/sbtprotoc/ProtocPlugin.scala#L515
    run(Seq(Target(gens.plugin("foo"), TmpPath))) must be(
      Seq(s"--foo_out=$TmpPath")
    )

    run(
      Seq(
        Target(gens.plugin("foo"), TmpPath),
        Target(gens.plugin("bar"), TmpPath2)
      )
    ) must be(Seq(s"--foo_out=$TmpPath", s"--bar_out=$TmpPath2"))

    run(
      Seq(
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath),
        Target(gens.plugin("bar"), TmpPath2)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-foo_0=/path/to/plugin",
        s"--foo_0_out=$TmpPath",
        s"--bar_out=$TmpPath2"
      )
    )

    run(
      Seq(
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath, Seq("w")),
        Target(gens.plugin("foo", "/path/to/plugin"), TmpPath, Seq("x")),
        Target(gens.plugin("foo", "/otherpath/to/plugin"), TmpPath1, Seq("y")),
        Target(gens.plugin("foo", "/otherpath/to/plugin"), TmpPath2, Seq("y")),
        Target(gens.plugin("foo"), TmpPath2, Seq("z")),
        Target(gens.plugin("bar"), TmpPath)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-foo_0=/path/to/plugin",
        "--plugin=protoc-gen-foo_1=/path/to/plugin",
        "--plugin=protoc-gen-foo_2=/otherpath/to/plugin",
        "--plugin=protoc-gen-foo_3=/otherpath/to/plugin",
        s"--foo_0_out=$TmpPath",
        s"--foo_0_opt=w",
        s"--foo_1_out=$TmpPath",
        s"--foo_1_opt=x",
        s"--foo_2_out=$TmpPath1",
        s"--foo_2_opt=y",
        s"--foo_3_out=$TmpPath2",
        s"--foo_3_opt=y",
        s"--foo_4_out=$TmpPath2",
        s"--foo_4_opt=z",
        s"--bar_out=$TmpPath"
      )
    )
  }

  val DefineFlag = "--plugin=protoc-gen-(.*?)=null".r
  val UseFlag = s"--(.*?)_out=${Pattern.quote(TmpPath.toString)}".r
  val UseFlagParams = s"--(.*?)_opt=x,y".r

  it should "allow using FooBarGen" in {
    run(Seq(Target(FoobarGen, TmpPath))) match {
      case Seq(DefineFlag(r), UseFlag(s)) if r == s =>
    }

    run(Seq(Target(FoobarGen, TmpPath, Seq("x", "y")))) match {
      case Seq(DefineFlag(r), UseFlag(s), UseFlagParams(o))
          if r == s && r == o =>
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
        s"--fff_0_out=$TmpPath",
        s"--fff_0_opt=x,y",
        s"--jvm_1_out=$TmpPath1",
        s"--fff_2_out=$TmpPath2",
        s"--fff_2_opt=foo,bar"
      )
    )
  }

  it should "allow using sandboxedGen multiple times" in {
    run(
      Seq(
        Target(sandboxedGen("x", "y"), TmpPath1),
        Target(sandboxedGen("foo", "bar"), TmpPath2)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-jvm_0=null",
        "--plugin=protoc-gen-jvm_1=null",
        s"--jvm_0_out=$TmpPath1",
        s"--jvm_0_opt=x,y",
        s"--jvm_1_out=$TmpPath2",
        s"--jvm_1_opt=foo,bar"
      )
    )
  }

  it should "preserve the order of targets" in {
    run(
      Seq(
        Target(sandboxedGen("sandboxed"), TmpPath1),
        Target(foobarGen("foo", "bar"), TmpPath2),
        FoobarGen -> TmpPath,
        Target(sandboxedGen("x", "y"), TmpPath2)
      )
    ) must be(
      Seq(
        "--plugin=protoc-gen-jvm_0=null",
        "--plugin=protoc-gen-fff_1=null",
        "--plugin=protoc-gen-jvm_2=null",
        "--plugin=protoc-gen-jvm_3=null",
        s"--jvm_0_out=$TmpPath1",
        s"--jvm_0_opt=sandboxed",
        s"--fff_1_out=$TmpPath2",
        s"--fff_1_opt=foo,bar",
        s"--jvm_2_out=$TmpPath",
        s"--jvm_3_out=$TmpPath2",
        s"--jvm_3_opt=x,y"
      )
    )
  }
}
