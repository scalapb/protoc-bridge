package protocbridge.frontend

class WindowsPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (PluginFrontend.isWindows) {
    it must "execute a program that forwards input and output to given stream" in {
      testSuccess(WindowsPluginFrontend)
    }

    it must "not hang if there is an OOM in generator" in {
      testFailure(WindowsPluginFrontend)
    }
  }
}
