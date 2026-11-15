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
        tools.put(tool("set_control_mode", "Choose how Claude plays. Only 'takeover_player' (control the user's live character) is available in this build.",
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
                        return text("companion_bot is not available in this build yet. Staying in takeover_player.", true);
                    }
                    return text("Unknown mode: " + m, true);
                }
                case "get_player_status": {
                    JSONObject o = new JSONObject().put("mode", McpState.mode)
                            .put("game_running", McpInput.gameReady())
                            .put("paused_by_user", McpState.paused)
                            .put("anti_afk", McpInput.isAntiAfk())
                            .put("note", "Coordinates, health, hunger and inventory need a client-side mod and are not available yet.");
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
                case "release_all":
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
        } catch (IllegalArgumentException | JSONException e) {
            return text(String.valueOf(e.getMessage()), true);
        }
    }
}
