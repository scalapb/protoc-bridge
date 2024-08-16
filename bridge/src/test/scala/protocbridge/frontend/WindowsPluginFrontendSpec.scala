package protocbridge.frontend

class WindowsPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (PluginFrontend.isWindows) {
    it must "execute a program that forwards input and output to given stream" in {
      val state = testSuccess(WindowsPluginFrontend)
      state.serverSocket.isClosed mustBe true
    }

    it must "not hang if there is an OOM in generator" in {
      val state = testFailure(WindowsPluginFrontend)
      state.serverSocket.isClosed mustBe true
    }
  }
}
