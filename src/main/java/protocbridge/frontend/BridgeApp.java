package protocbridge.frontend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class BridgeApp {
    /**
     * Simple pure Java application that connects to TCP port (host is 127.0.0.1, port is first argument).
     * It redirects stdin and stdout to/from this socket.
     */
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            // read stdin and write it to the socket
            byte[] input = readInputStreamToByteArray(System.in);
            socket.getOutputStream().write(input);
            socket.shutdownOutput();
            // read the socket and write bytes to stdout
            System.out.write(readInputStreamToByteArray(socket.getInputStream()));
        }
    }

    private static byte[] readInputStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count = 0;
        while (count != -1) {
            count = is.read(buffer);
            if (count > 0) {
                b.write(buffer, 0, count);
            }
        }
        return b.toByteArray();
    }
}
