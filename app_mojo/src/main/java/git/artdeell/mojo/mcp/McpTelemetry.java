package git.artdeell.mojo.mcp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Reads the live snapshot published by the MCP Telemetry Fabric mod on 127.0.0.1:3001. */
final class McpTelemetry {
    private McpTelemetry() {}

    static final int PORT = 3001;

    /** Returns the latest snapshot, or null if the mod is not installed / game not running. */
    static JSONObject read() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 400);
            s.setSoTimeout(600);
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String line = r.readLine();
            return line == null ? null : new JSONObject(line);
        } catch (Exception e) {
            return null;
        }
    }

    static double wrap180(double a) {
        return ((a % 360) + 540) % 360 - 180;
    }
}
