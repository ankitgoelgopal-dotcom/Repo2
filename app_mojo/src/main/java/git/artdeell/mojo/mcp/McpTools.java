package git.artdeell.mojo.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** MCP tool definitions and their implementation (Mode B: take over the live player). */
final class McpTools {
    private McpTools() {}

    private static JSONObject prop(String type, String desc) throws JSONException {
        return new JSONObject().put("type", type).put("description", desc);
    }

    private static JSONObject tool(String name, String desc, JSONObject props, String... required) throws JSONException {
        JSONObject schema = new JSONObject().put("type", "object").put("properties", props);
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        schema.put("required", req);
        return new JSONObject().put("name", name).put("description", desc).put("inputSchema", schema);
    }

    static JSONArray list() throws JSONException {
        JSONArray actions = new JSONArray();
        for (String a : McpInput.actionNames()) actions.put(a);
        JSONArray modes = new JSONArray().put(McpState.MODE_TAKEOVER).put(McpState.MODE_COMPANION);
        JSONArray tools = new JSONArray();
        tools.put(tool("set_control_mode", "Choose how Claude plays: 'takeover_player' controls the user's own character; 'companion_bot' controls a separate Carpet fake player that plays alongside the user.",
                new JSONObject().put("mode", prop("string", "takeover_player or companion_bot").put("enum", modes)), "mode"));
        tools.put(tool("get_player_status", "Current control mode, whether the game is running, and whether the user has paused Claude. "
                + "Coordinates, health and inventory are NOT available yet (needs a client mod).", new JSONObject()));
        tools.put(tool("send_chat", "Type a chat message, or a command if it starts with '/'. Best effort for ASCII text.",
                new JSONObject().put("message", prop("string", "Text to send")), "message"));
        tools.put(tool("look_by", "Turn the camera by a relative amount in degrees. Positive yaw = right, positive pitch = down. This is the most reliable look tool.",
                new JSONObject().put("yaw_delta", prop("number", "Degrees to turn right (negative = left)"))
                        .put("pitch_delta", prop("number", "Degrees to look down (negative = up)"))));
        tools.put(tool("look_at", "Absolute camera angles relative to the facing when control started (yaw 0, pitch 0). Approximate; prefer look_by.",
                new JSONObject().put("yaw", prop("number", "Yaw in degrees")).put("pitch", prop("number", "Pitch in degrees, -90..90")),
                "yaw", "pitch"));
        tools.put(tool("perform_action", "Hold a control for a duration: movement (forward/back/left/right), jump, sneak, sprint, attack (mine/hit), "
                        + "use (place/interact), hotbar_slot (needs 'slot' 1-9), inventory, drop, swap_hands. Max 30000 ms.",
                new JSONObject().put("action", prop("string", "Which action").put("enum", actions))
                        .put("duration_ms", prop("integer", "How long to hold, in milliseconds"))
                        .put("slot", prop("integer", "Hotbar slot 1-9, only for hotbar_slot")), "action"));
        tools.put(tool("move_to", "Walk in a straight line to world coordinates (no pathfinding; it hops small obstacles). Runs in the background: poll get_player_status. "
                        + "Needs the MCP Telemetry mod.",
                new JSONObject().put("x", prop("number", "Target X")).put("y", prop("number", "Target Y"))
                        .put("z", prop("number", "Target Z")).put("timeout_ms", prop("integer", "Give up after this long (default 60000)")),
                "x", "y", "z"));
        tools.put(tool("follow_player", "Keep walking toward a nearby player or entity by name until stopped (release_all stops it). Needs the telemetry mod; the target must be within 48 blocks.",
                new JSONObject().put("player_name", prop("string", "Exact player name")), "player_name"));
        tools.put(tool("mine_block", "Aim at the block at (x,y,z) and hold attack. Move within ~4 blocks first. Does not verify the block broke.",
                new JSONObject().put("x", prop("integer", "Block X")).put("y", prop("integer", "Block Y")).put("z", prop("integer", "Block Z"))
                        .put("duration_ms", prop("integer", "How long to mine (default 2500, max 30000)")), "x", "y", "z"));
        tools.put(tool("place_block", "Aim at the top face of the block under (x,y,z) and use the held item once, so a block is placed at (x,y,z). Select the item with perform_action hotbar_slot first.",
                new JSONObject().put("x", prop("integer", "Target X")).put("y", prop("integer", "Target Y")).put("z", prop("integer", "Target Z")), "x", "y", "z"));
        JSONArray onOff = new JSONArray().put("on").put("off");
        tools.put(tool("bot_spawn", "MODE A (companion_bot): spawn the Carpet fake player that plays alongside the user, at the user's position. Needs the Carpet mod and operator rights.",
                new JSONObject().put("name", prop("string", "Bot name, 1-16 letters/digits/_ (default Claude)"))));
        tools.put(tool("bot_kill", "MODE A: remove the bot from the world.", new JSONObject()));
        tools.put(tool("bot_stop", "MODE A: stop everything the bot is doing (walking, mining, jumping).", new JSONObject()));
        tools.put(tool("bot_move", "MODE A: walk the bot in a direction until bot_stop, or for duration_ms.",
                new JSONObject().put("direction", prop("string", "forward, backward, left or right"))
                        .put("duration_ms", prop("integer", "Optional: stop after this many ms")), "direction"));
        tools.put(tool("bot_look_at", "MODE A: turn the bot to face world coordinates.",
                new JSONObject().put("x", prop("number", "X")).put("y", prop("number", "Y")).put("z", prop("number", "Z")), "x", "y", "z"));
        tools.put(tool("bot_look", "MODE A: set the bot's camera angles in degrees (yaw 0=south, 90=west; pitch positive=down).",
                new JSONObject().put("yaw", prop("number", "Yaw")).put("pitch", prop("number", "Pitch")), "yaw", "pitch"));
        tools.put(tool("bot_attack", "MODE A: bot hits/mines whatever it is looking at.",
                new JSONObject().put("mode", prop("string", "once or continuous (continuous mines until bot_stop)")), "mode"));
        tools.put(tool("bot_use", "MODE A: bot uses/places/interacts with the held item.",
                new JSONObject().put("mode", prop("string", "once or continuous")), "mode"));
        tools.put(tool("bot_jump", "MODE A: bot jumps.", new JSONObject().put("mode", prop("string", "once or continuous")), "mode"));
        tools.put(tool("bot_sneak", "MODE A: toggle bot sneaking.", new JSONObject().put("state", prop("string", "on or off").put("enum", onOff)), "state"));
        tools.put(tool("bot_sprint", "MODE A: toggle bot sprinting.", new JSONObject().put("state", prop("string", "on or off").put("enum", onOff)), "state"));
        tools.put(tool("bot_hotbar", "MODE A: select the bot's hotbar slot.", new JSONObject().put("slot", prop("integer", "1-9")), "slot"));
        tools.put(tool("bot_say", "MODE A: make the bot say something in chat.", new JSONObject().put("message", prop("string", "Text")), "message"));
        tools.put(tool("bot_follow_me", "MODE A: bot keeps walking toward the user until bot_stop. Needs the telemetry mod; the bot must be within 48 blocks.",
                new JSONObject().put("distance", prop("number", "How close to stay, default 3"))));
        tools.put(tool("bot_goto", "MODE A: bot walks in a straight line to coordinates (no pathfinding, hops small obstacles).",
                new JSONObject().put("x", prop("number", "X")).put("y", prop("number", "Y")).put("z", prop("number", "Z"))
                        .put("timeout_ms", prop("integer", "Default 60000")), "x", "y", "z"));
        tools.put(tool("release_all", "Release every held key and mouse button immediately.", new JSONObject()));
        tools.put(tool("toggle_anti_afk", "Enable/disable subtle randomized movements every ~20-45 s to avoid idle kicks.",
                new JSONObject().put("enabled", prop("boolean", "true to enable")), "enabled"));
        return tools;
    }

    private static JSONObject text(String t, boolean error) throws JSONException {
        return new JSONObject()
                .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", t)))
                .put("isError", error);
    }

    static JSONObject call(String name, JSONObject args) throws JSONException {
        if (args == null) args = new JSONObject();
        // Mode gating: takeover tools only in takeover_player, bot_* tools only in companion_bot.
        boolean botTool = name.startsWith("bot_");
        boolean takeoverTool = name.equals("send_chat") || name.equals("look_by") || name.equals("look_at")
                || name.equals("perform_action") || name.equals("move_to") || name.equals("follow_player")
                || name.equals("mine_block") || name.equals("place_block") || name.equals("toggle_anti_afk");
        if (botTool && !McpState.MODE_COMPANION.equals(McpState.mode)) {
            return text("This is a companion_bot tool but the mode is " + McpState.mode + ". Call set_control_mode with companion_bot first.", true);
        }
        if (takeoverTool && !McpState.MODE_TAKEOVER.equals(McpState.mode)) {
            return text("This tool controls the user's own character, but the mode is " + McpState.mode
                    + ". Use the bot_* tools, or call set_control_mode with takeover_player.", true);
        }
        // Read-only / safety tools keep working while paused.
        boolean safe = name.equals("get_player_status") || name.equals("release_all") || name.equals("set_control_mode");
        if (McpState.paused && !safe) {
            return text("The user pressed Emergency Stop. Control is paused until they resume it. Do not retry; ask the user.", true);
        }
        try {
            switch (name) {
                case "set_control_mode": {
                    String m = args.optString("mode");
                    if (McpState.MODE_TAKEOVER.equals(m)) {
                        McpState.mode = m;
                        return text("Mode set to takeover_player.", false);
                    }
                    if (McpState.MODE_COMPANION.equals(m)) {
                        McpState.mode = m;
                        McpInput.releaseAll();
                        return text("Mode set to companion_bot. Use bot_spawn first (needs Carpet + operator rights).", false);
                    }
                    return text("Unknown mode: " + m, true);
                }
                case "get_player_status": {
                    JSONObject tel = McpTelemetry.read();
                    JSONObject o = new JSONObject().put("mode", McpState.mode)
                            .put("navigation", McpNav.status())
                            .put("bot", McpBot.statusJson())
                            .put("telemetry", tel == null ? JSONObject.NULL : tel)
                            .put("game_running", McpInput.gameReady())
                            .put("paused_by_user", McpState.paused)
                            .put("anti_afk", McpInput.isAntiAfk())
                            .put("note", tel == null ? "Telemetry mod not reachable: install the MCP Telemetry mod (Fabric 1.21.11) to get coordinates, health and inventory." : "ok");
                    return text(o.toString(), false);
                }
                case "send_chat":
                    McpInput.chat(args.getString("message"));
                    return text("Sent.", false);
                case "look_by":
                    McpInput.lookBy(args.optDouble("yaw_delta", 0), args.optDouble("pitch_delta", 0));
                    return text("Turning camera.", false);
                case "look_at":
                    McpInput.lookAt(args.getDouble("yaw"), args.getDouble("pitch"));
                    return text("Turning camera.", false);
                case "perform_action":
                    return text("Started: " + McpInput.perform(args.getString("action"),
                            args.optLong("duration_ms", 0), args.optInt("slot", 0)), false);
                case "move_to":
                    return text(McpNav.moveTo(args.getDouble("x"), args.getDouble("y"), args.getDouble("z"),
                            Math.max(1000, Math.min(args.optLong("timeout_ms", 60_000), 600_000))), false);
                case "follow_player":
                    return text(McpNav.follow(args.getString("player_name"), 300_000), false);
                case "mine_block": {
                    McpNav.aimAt(args.getInt("x") + 0.5, args.getInt("y") + 0.5, args.getInt("z") + 0.5);
                    McpInput.perform("attack", Math.max(200, Math.min(args.optLong("duration_ms", 2500), 30_000)), 0);
                    return text("Mining started.", false);
                }
                case "place_block": {
                    McpNav.aimAt(args.getInt("x") + 0.5, args.getInt("y"), args.getInt("z") + 0.5);
                    McpInput.perform("use", 120, 0);
                    return text("Placed (aimed at the top of the block below).", false);
                }
                case "bot_spawn":
                    if (args.has("name")) McpBot.setName(args.getString("name"));
                    McpBot.cmd("spawn");
                    return text("Asked Carpet to spawn the bot '" + McpBot.name() + "'. If nothing appears, Carpet is missing or the player is not an operator.", false);
                case "bot_kill":
                    McpBot.cancel();
                    McpBot.cmd("kill");
                    return text("Bot removed.", false);
                case "bot_stop":
                    McpBot.stop();
                    return text("Bot stopped.", false);
                case "bot_move": {
                    String d = args.getString("direction").toLowerCase(java.util.Locale.ROOT);
                    if (!(d.equals("forward") || d.equals("backward") || d.equals("left") || d.equals("right"))) {
                        return text("direction must be forward, backward, left or right", true);
                    }
                    McpBot.cmd("move " + d);
                    long dur = args.optLong("duration_ms", 0);
                    if (dur > 0) {
                        final long ms = Math.min(dur, 60_000);
                        Thread th = new Thread(() -> {
                            try {
                                Thread.sleep(ms);
                                McpBot.cmd("stop");
                            } catch (Exception ignored) {
                            }
                        }, "mcp-bot-move");
                        th.setDaemon(true);
                        th.start();
                    }
                    return text("Bot moving " + d + ".", false);
                }
                case "bot_look_at":
                    McpBot.cmd(String.format(java.util.Locale.ROOT, "look at %.2f %.2f %.2f",
                            args.getDouble("x"), args.getDouble("y"), args.getDouble("z")));
                    return text("Bot turning.", false);
                case "bot_look":
                    McpBot.cmd(String.format(java.util.Locale.ROOT, "look %.1f %.1f", args.getDouble("yaw"), args.getDouble("pitch")));
                    return text("Bot turning.", false);
                case "bot_attack":
                case "bot_use":
                case "bot_jump": {
                    String mode = args.getString("mode").toLowerCase(java.util.Locale.ROOT);
                    if (!(mode.equals("once") || mode.equals("continuous"))) return text("mode must be once or continuous", true);
                    McpBot.cmd(name.substring(4) + " " + mode);
                    return text("Done: " + name + " " + mode, false);
                }
                case "bot_sneak":
                    McpBot.cmd("on".equalsIgnoreCase(args.getString("state")) ? "sneak" : "unsneak");
                    return text("OK", false);
                case "bot_sprint":
                    McpBot.cmd("on".equalsIgnoreCase(args.getString("state")) ? "sprint" : "unsprint");
                    return text("OK", false);
                case "bot_hotbar": {
                    int slot = args.getInt("slot");
                    if (slot < 1 || slot > 9) return text("slot must be 1-9", true);
                    McpBot.cmd("hotbar " + slot);
                    return text("Bot hotbar slot " + slot, false);
                }
                case "bot_say":
                    McpBot.say(args.getString("message"));
                    return text("Bot said it.", false);
                case "bot_follow_me":
                    return text(McpBot.followMe(args.optDouble("distance", 3)), false);
                case "bot_goto":
                    return text(McpBot.goTo(args.getDouble("x"), args.getDouble("y"), args.getDouble("z"),
                            Math.max(1000, Math.min(args.optLong("timeout_ms", 60_000), 600_000))), false);
                case "release_all":
                    McpBot.cancel();
                    McpInput.releaseAll();
                    return text("Released all inputs.", false);
                case "toggle_anti_afk": {
                    boolean en = args.getBoolean("enabled");
                    McpInput.setAntiAfk(en);
                    return text("Anti-AFK " + (en ? "enabled" : "disabled") + ".", false);
                }
                default:
                    return text("Unknown tool: " + name, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return text("Interrupted.", true);
        } catch (IllegalArgumentException | JSONException e) {
            return text(String.valueOf(e.getMessage()), true);
        }
    }
}
