package dev.mojomcp.telemetry;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Tiny loopback-only server: every connection receives the latest JSON snapshot, then is closed. */
final class TelemetryServer {
    private TelemetryServer() {}

    static void start(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
                while (true) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(500);
                        OutputStream out = s.getOutputStream();
                        out.write((McpTelemetryMod.latest + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (Exception ignored) {
                        // a bad client must never stop the server
                    }
                }
            } catch (Exception e) {
                // port already in use or sockets blocked: telemetry simply stays unavailable
            }
        }, "mcp-telemetry-server");
        t.setDaemon(true);
        t.start();
    }
}
