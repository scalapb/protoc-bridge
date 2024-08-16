package protocbridge.frontend

class PosixPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (!PluginFrontend.isWindows && !PluginFrontend.isMac) {
    it must "execute a program that forwards input and output to given stream" in {
      testSuccess(PosixPluginFrontend)
    }

    it must "not hang if there is an OOM in generator" in {
      testFailure(PosixPluginFrontend)
    }
  }
}
