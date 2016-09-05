package protocbridge

import org.scalatest._

import java.io.File
import Target.builtin

class TargetSpec extends FlatSpec with MustMatchers {
  val TmpPath = new File("/tmp")

  object FoobarGen extends ProtocCodeGenerator {
    import com.google.protobuf.compiler.PluginProtos.{CodeGeneratorResponse, CodeGeneratorRequest}

    override def run(request: CodeGeneratorRequest): CodeGeneratorResponse = CodeGeneratorResponse.newBuilder.build
  }

  def foobarGen(opt1: String, opt2: String): (Generator, Seq[String]) =
    (JvmGenerator("fff", FoobarGen), Seq(opt1, opt2))

  "target" should "lift string to BuiltinGeneratorCall" in {
    Target(builtin("java"), TmpPath) must matchPattern {
      case Target(BuiltinGenerator("java", Nil), TmpPath, Nil) =>
    }
    (builtin("java") -> TmpPath: Target) must matchPattern {
      case Target(BuiltinGenerator("java", Nil), TmpPath, Nil) =>
    }
  }

  it should "allow passing options to string generator" in {
    Target(builtin("java", Seq("opt1", "opt2")), TmpPath) must matchPattern {
      case Target(BuiltinGenerator("java", Nil), TmpPath, Seq("opt1", "opt2")) =>
    }

    (builtin("java", Seq("opt1", "opt2")) -> TmpPath: Target) must matchPattern {
      case Target(BuiltinGenerator("java", Nil), TmpPath, Seq("opt1", "opt2")) =>
    }
  }

  it should "allow predefined builtin constants" in {
    Target(gens.java, TmpPath) must matchPattern {
      case Target(BuiltinGenerator(
        "java", List(Artifact("com.google.protobuf", "protobuf-java", _, false))), TmpPath, Nil) =>
    }
  }

  it should "allow passing options to predefined plugins" in {
    Target(gens.java, TmpPath, Seq("ffx")) must matchPattern {
      case Target(BuiltinGenerator(
        "java", List(Artifact("com.google.protobuf", "protobuf-java", _, false))), TmpPath, Seq("ffx")) =>
    }

    ((gens.java, Seq("ffx")) -> TmpPath: Target) must matchPattern {
      case Target(BuiltinGenerator(
      "java", List(Artifact("com.google.protobuf", "protobuf-java", _, false))), TmpPath, Seq("ffx")) =>
    }
  }

  it should "using ProtocCodeGenerator and assigning random name" in {
    Target(FoobarGen, TmpPath) must matchPattern {
      case Target(JvmGenerator(name, FoobarGen), TmpPath, Nil) if name.startsWith("jvm_") =>
    }

    (FoobarGen -> TmpPath: Target) must matchPattern {
      case Target(JvmGenerator(name, FoobarGen), TmpPath, Nil) if name.startsWith("jvm_") =>
    }

    ((FoobarGen, Seq("x", "y")) -> TmpPath: Target) must matchPattern {
      case Target(JvmGenerator(name, FoobarGen), TmpPath, Seq("x", "y")) if name.startsWith("jvm_") =>
    }
  }

  it should "allow using the options syntax" in {
    Target(foobarGen("xyz", "wf"), TmpPath) must matchPattern {
      case Target(JvmGenerator("fff", FoobarGen), TmpPath, Seq("xyz", "wf")) =>
    }

    (foobarGen("xyz", "wf") -> TmpPath: Target) must matchPattern {
      case Target(JvmGenerator("fff", FoobarGen), TmpPath, Seq("xyz", "wf")) =>
    }
  }
}

