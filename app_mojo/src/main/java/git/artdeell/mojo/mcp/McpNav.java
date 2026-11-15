package git.artdeell.mojo.mcp;

import org.json.JSONArray;
import org.json.JSONObject;

/** Simple closed-loop navigation using mod telemetry. Straight-line walking, no pathfinding. */
final class McpNav {
    private McpNav() {}

    private interface Target {
        /** Returns {x, y, z} of the target, or null if it can't be found right now. */
        double[] get(JSONObject t);
    }

    private static volatile Thread thread;
    private static volatile boolean cancelled = false;
    private static volatile String status = "idle";

    static String status() {
        return status;
    }

    static synchronized void cancel() {
        cancelled = true;
        Thread t = thread;
        if (t != null) t.interrupt();
        if (t != null) status = "cancelled";
    }

    private static synchronized void begin(final String label, final Target target, final double stopDist, final long timeoutMs) {
        cancel();
        cancelled = false;
        status = label + ": starting";
        thread = new Thread(() -> run(label, target, stopDist, timeoutMs), "mcp-nav");
        thread.setDaemon(true);
        thread.start();
    }

    static String moveTo(final double x, final double y, final double z, long timeoutMs) {
        requireTelemetry();
        begin("move_to " + Math.round(x) + "," + Math.round(y) + "," + Math.round(z),
                t -> new double[]{x, y, z}, 1.2, timeoutMs);
        return "Walking to " + x + ", " + y + ", " + z + ". Poll get_player_status to see progress.";
    }

    static String follow(final String name, long timeoutMs) {
        requireTelemetry();
        begin("follow " + name, t -> {
            JSONArray ents = t.optJSONArray("entities");
            if (ents == null) return null;
            for (int i = 0; i < ents.length(); i++) {
                JSONObject e = ents.optJSONObject(i);
                if (e != null && name.equalsIgnoreCase(e.optString("name"))) {
                    return new double[]{e.optDouble("x"), e.optDouble("y"), e.optDouble("z")};
                }
            }
            return null;
        }, 3.0, timeoutMs);
        return "Following " + name + " (stops within 3 blocks, keeps following until stopped).";
    }

    static void requireTelemetry() {
        JSONObject t = McpTelemetry.read();
        if (t == null) {
            throw new IllegalArgumentException("Telemetry mod not reachable. Install the MCP Telemetry mod "
                    + "(Fabric, Minecraft 1.21.11) in the instance you are playing and join a world.");
        }
        if (!t.optBoolean("in_world")) throw new IllegalArgumentException("The player is not in a world yet.");
    }

    private static void sleep(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    private static long turnTime(double dYaw, double dPitch) {
        int steps = (int) Math.max(1, Math.min(30, Math.hypot(dYaw, dPitch) / 3));
        return steps * 15L + 60;
    }

    private static void run(String label, Target target, double stopDist, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        double lastX = Double.NaN, lastZ = Double.NaN;
        long lastCheck = System.currentTimeMillis();
        try {
            while (!cancelled && System.currentTimeMillis() < end) {
                JSONObject t = McpTelemetry.read();
                if (t == null || !t.optBoolean("in_world")) {
                    status = label + ": waiting for telemetry";
                    sleep(500);
                    continue;
                }
                double px = t.optDouble("x"), py = t.optDouble("y"), pz = t.optDouble("z"), yaw = t.optDouble("yaw");
                double[] tg = target.get(t);
                if (tg == null) {
                    status = label + ": target not visible";
                    sleep(500);
                    continue;
                }
                double dx = tg[0] - px, dz = tg[2] - pz, dist = Math.hypot(dx, dz);
                if (dist < stopDist && Math.abs(tg[1] - py) < 3.0) {
                    status = label + ": arrived (" + Math.round(px) + "," + Math.round(py) + "," + Math.round(pz) + ")";
                    return;
                }
                double desired = Math.toDegrees(Math.atan2(-dx, dz));
                double err = McpTelemetry.wrap180(desired - yaw);
                if (Math.abs(err) > 4) {
                    double d = Math.max(-45, Math.min(45, err));
                    McpInput.lookBy(d, 0);
                    sleep(turnTime(d, 0));
                }
                status = label + ": " + Math.round(dist) + " blocks left";
                if (Math.abs(err) < 25) {
                    McpInput.perform("forward", 350, 0);
                    if (dist > 6) McpInput.perform("sprint", 350, 0);
                    // Hop up steps / over obstacles: target is higher, or we have stopped making progress.
                    long now = System.currentTimeMillis();
                    if (tg[1] > py + 0.6) McpInput.perform("jump", 250, 0);
                    if (now - lastCheck > 800) {
                        if (!Double.isNaN(lastX) && Math.hypot(px - lastX, pz - lastZ) < 0.15) {
                            McpInput.perform("jump", 300, 0);
                        }
                        lastX = px;
                        lastZ = pz;
                        lastCheck = now;
                    }
                }
                sleep(200);
            }
            if (!cancelled) status = label + ": timed out";
        } catch (InterruptedException ignored) {
        } catch (RuntimeException e) {
            status = label + ": error " + e.getMessage();
        } finally {
            McpInput.releaseMovement();
        }
    }

    /** Aim the camera at a point in the world (blocking, ~1 s). Needs telemetry. */
    static void aimAt(double x, double y, double z) throws InterruptedException {
        requireTelemetry();
        for (int pass = 0; pass < 2; pass++) {
            JSONObject t = McpTelemetry.read();
            if (t == null) throw new IllegalArgumentException("Telemetry lost.");
            double px = t.optDouble("x"), py = t.optDouble("y") + 1.62, pz = t.optDouble("z");
            double dx = x - px, dy = y - py, dz = z - pz, h = Math.hypot(dx, dz);
            double yawErr = McpTelemetry.wrap180(Math.toDegrees(Math.atan2(-dx, dz)) - t.optDouble("yaw"));
            double pitchErr = -Math.toDegrees(Math.atan2(dy, h)) - t.optDouble("pitch");
            McpInput.lookBy(yawErr, pitchErr);
            sleep(turnTime(yawErr, pitchErr) + 150);
        }
    }
}
