package dev.mojomcp.telemetry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Loopback-only command channel for the companion bot. Accepts ONE line per connection and only
 * forwards a tight allow-list: Carpet "/player <name> <action>" and "/execute as <name> run say <text>".
 * Everything else is refused, so this channel can never run /op, /gamemode, /give and the like.
 */
final class CommandServer {
    private CommandServer() {}

    private static final Pattern PLAYER = Pattern.compile(
            "^player [A-Za-z0-9_]{1,16} (spawn|kill|move|look|attack|use|jump|sneak|unsneak|sprint|unsprint|stop|hotbar|drop|dropStack|swapHands|turn|mount|dismount)( [A-Za-z0-9_.~^ -]{0,60})?$");
    private static final Pattern SAY = Pattern.compile(
            "^execute as [A-Za-z0-9_]{1,16} run say [^\\r\\n/\\u00a7\\\\\"]{1,200}$");

    static boolean allowed(String c) {
        return c != null && c.length() <= 300 && (PLAYER.matcher(c).matches() || SAY.matcher(c).matches());
    }

    static void start(int port) {
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
                while (true) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(1000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        String line = r.readLine();
                        boolean ok = allowed(line) && McpTelemetryMod.COMMANDS.size() < 64;
                        if (ok) McpTelemetryMod.COMMANDS.add(line);
                        OutputStream out = s.getOutputStream();
                        out.write((ok ? "ok\n" : "denied\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (Exception ignored) {
                        // a bad client must never stop the server
                    }
                }
            } catch (Exception e) {
                // port in use: bot commands simply stay unavailable
            }
        }, "mcp-command-server");
        t.setDaemon(true);
        t.start();
    }
}
