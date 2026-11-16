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

    static final int COMMAND_PORT = 3002;

    /** Sends one allow-listed bot command (no leading slash) to the telemetry mod. */
    static void sendCommand(String command) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), COMMAND_PORT), 400);
            s.setSoTimeout(800);
            s.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            String reply = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
            if (!"ok".equals(reply)) throw new IllegalArgumentException("The telemetry mod refused the command: " + command);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Bot command channel not reachable. Install the MCP Telemetry mod "
                    + "(updated version) and join a world.");
        }
    }

    static double wrap180(double a) {
        return ((a % 360) + 540) % 360 - 180;
    }
}
