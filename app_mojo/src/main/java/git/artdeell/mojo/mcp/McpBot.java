package git.artdeell.mojo.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mode A: a Carpet fake player that plays alongside the user. Commands go to the telemetry mod, which
 * sends them silently as /player commands (needs Carpet + operator rights in the world).
 */
final class McpBot {
    private McpBot() {}

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static volatile String name = "Claude";
    private static volatile Thread thread;
    private static volatile boolean cancelled = false;
    private static volatile String status = "idle";

    static String name() {
        return name;
    }

    static String navStatus() {
        return status;
    }

    static void setName(String n) {
        if (n == null || !NAME.matcher(n).matches()) throw new IllegalArgumentException("Bot name must be 1-16 letters, digits or _.");
        name = n;
    }

    static void cmd(String action) {
        McpTelemetry.sendCommand("player " + name + " " + action);
    }

    static synchronized void cancel() {
        cancelled = true;
        Thread t = thread;
        if (t != null) t.interrupt();
        status = "idle";
    }

    static void stop() {
        cancel();
        cmd("stop");
    }

    static void say(String message) {
        String m = message.replace("\n", " ").replace("\r", " ").replace("/", " ").replace("\"", "'").replace("\\", " ");
        if (m.length() > 200) m = m.substring(0, 200);
        if (m.trim().isEmpty()) throw new IllegalArgumentException("Empty message.");
        McpTelemetry.sendCommand("execute as " + name + " run say " + m);
    }

    // ---- locating things via telemetry -----------------------------------------------------------

    static double[] botPosition(JSONObject t) {
        JSONArray ents = t.optJSONArray("entities");
        if (ents == null) return null;
        for (int i = 0; i < ents.length(); i++) {
            JSONObject e = ents.optJSONObject(i);
            if (e != null && name.equalsIgnoreCase(e.optString("name"))) {
                return new double[]{e.optDouble("x"), e.optDouble("y"), e.optDouble("z")};
            }
        }
        return null;
    }

    static JSONObject statusJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("bot_name", name).put("navigation", status);
            JSONObject t = McpTelemetry.read();
            if (t != null && t.optBoolean("in_world")) {
                double[] b = botPosition(t);
                if (b == null) {
                    o.put("bot_visible", false).put("hint", "Bot not within 48 blocks (or not spawned). Use bot_spawn.");
                } else {
                    double dx = b[0] - t.optDouble("x"), dz = b[2] - t.optDouble("z");
                    o.put("bot_visible", true).put("bot_x", b[0]).put("bot_y", b[1]).put("bot_z", b[2])
                            .put("distance_to_player", Math.round(Math.hypot(dx, dz) * 10) / 10.0);
                }
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    // ---- walking: follow the player or go to a point -----------------------------------------------

    private interface Target {
        double[] get(JSONObject t);
    }

    static String followMe(double distance) {
        return begin("follow player", t -> new double[]{t.optDouble("x"), t.optDouble("y"), t.optDouble("z")},
                Math.max(2, distance), 600_000);
    }

    static String goTo(final double x, final double y, final double z, long timeoutMs) {
        return begin("go to " + Math.round(x) + "," + Math.round(y) + "," + Math.round(z), t -> new double[]{x, y, z}, 1.5, timeoutMs);
    }

    private static synchronized String begin(final String label, final Target target, final double stopDist, final long timeoutMs) {
        McpNav.requireTelemetry();
        cancel();
        cancelled = false;
        status = label + ": starting";
        thread = new Thread(() -> run(label, target, stopDist, timeoutMs), "mcp-bot-nav");
        thread.setDaemon(true);
        thread.start();
        return "Bot is walking (" + label + "). Poll get_player_status; bot_stop cancels.";
    }

    private static void run(String label, Target target, double stopDist, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        boolean moving = false;
        double lastX = Double.NaN, lastZ = Double.NaN;
        long lastCheck = System.currentTimeMillis();
        try {
            while (!cancelled && System.currentTimeMillis() < end) {
                JSONObject t = McpTelemetry.read();
                double[] bot = t == null ? null : botPosition(t);
                if (t == null || !t.optBoolean("in_world") || bot == null) {
                    status = label + ": bot not visible";
                    if (moving) { cmd("stop"); moving = false; }
                    Thread.sleep(1000);
                    continue;
                }
                double[] tg = target.get(t);
                double dx = tg[0] - bot[0], dz = tg[2] - bot[2], dist = Math.hypot(dx, dz);
                if (dist < stopDist && Math.abs(tg[1] - bot[1]) < 3.0) {
                    if (moving) { cmd("stop"); moving = false; }
                    if (label.startsWith("follow")) { // keep following: wait until the player walks away again
                        status = label + ": close enough";
                        Thread.sleep(800);
                        continue;
                    }
                    status = label + ": arrived";
                    return;
                }
                cmd(String.format(Locale.ROOT, "look at %.2f %.2f %.2f", tg[0], tg[1] + 1.0, tg[2]));
                if (!moving) {
                    cmd("move forward");
                    if (dist > 8) cmd("sprint");
                    moving = true;
                }
                long now = System.currentTimeMillis();
                if (tg[1] > bot[1] + 0.6) cmd("jump once");
                if (now - lastCheck > 1400) {
                    if (!Double.isNaN(lastX) && Math.hypot(bot[0] - lastX, bot[2] - lastZ) < 0.2) cmd("jump once");
                    lastX = bot[0];
                    lastZ = bot[2];
                    lastCheck = now;
                }
                status = label + ": " + Math.round(dist) + " blocks left";
                Thread.sleep(700);
            }
            if (!cancelled) status = label + ": timed out";
        } catch (InterruptedException ignored) {
        } catch (RuntimeException e) {
            status = label + ": error " + e.getMessage();
        } finally {
            try { cmd("stop"); } catch (RuntimeException ignored) { }
        }
    }
}
