package protocbridge.frontend

class PosixPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (!PluginFrontend.isWindows && !PluginFrontend.isMac) {
    it must "execute a program that forwards input and output to given stream" in {
      testSuccess(MacPluginFrontend)
    }

    it must "not hang if there is an OOM in generator" in {
      testFailure(MacPluginFrontend)
    }
  }
}
