package protocbridge.frontend

class PosixPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (!PluginFrontend.isWindows) {
    it must "execute a program that forwards input and output to given stream" in {
      testPluginFrontend(PosixPluginFrontend)
    }
  }
}
