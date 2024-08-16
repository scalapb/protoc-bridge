package protocbridge.frontend

class MacPluginFrontendSpec extends OsSpecificFrontendSpec {
  if (PluginFrontend.isMac) {
    it must "execute a program that forwards input and output to given stream" in {
      val state = testSuccess(MacPluginFrontend)
      state.serverSocket.isClosed mustBe true
    }

    it must "not hang if there is an error in generator" in {
      val state = testFailure(MacPluginFrontend)
      state.serverSocket.isClosed mustBe true
    }
  }
}
