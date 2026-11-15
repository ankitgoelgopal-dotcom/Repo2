package git.artdeell.mojo.mcp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Minimal MCP server (Streamable HTTP transport, JSON responses only) bound to 127.0.0.1.
 * The endpoint is /{token}/mcp - the random token is the only credential, so the public tunnel URL
 * must be treated like a password.
 */
public class McpServer extends NanoHTTPD {
    public interface Listener {
        void onClientActivity();
    }

    private final String token;
    private final Listener listener;

    public McpServer(int port, String token, Listener listener) {
        super("127.0.0.1", port);
        this.token = token;
        this.listener = listener;
    }

    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Headers", "*");
        r.addHeader("Access-Control-Allow-Methods", "POST, GET, DELETE, OPTIONS");
        r.addHeader("Access-Control-Expose-Headers", "Mcp-Session-Id");
        return r;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        String expected = "/" + token + "/mcp";
        if (!MessageDigest.isEqual(uri.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        }
        Method m = session.getMethod();
        if (m == Method.OPTIONS) return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""));
        if (m == Method.DELETE) return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
        if (m != Method.POST) {
            Response r = newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "POST only");
            r.addHeader("Allow", "POST");
            return cors(r);
        }
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            if (body == null) return cors(newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Empty body"));
            listener.onClientActivity();
            Object parsed = new org.json.JSONTokener(body).nextValue();
            if (parsed instanceof JSONArray) {
                JSONArray in = (JSONArray) parsed, out = new JSONArray();
                for (int i = 0; i < in.length(); i++) {
                    JSONObject r = handle(in.getJSONObject(i));
                    if (r != null) out.put(r);
                }
                if (out.length() == 0) return cors(newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain", ""));
                return cors(newFixedLengthResponse(Response.Status.OK, "application/json", out.toString()));
            }
            JSONObject r = handle((JSONObject) parsed);
            if (r == null) return cors(newFixedLengthResponse(Response.Status.ACCEPTED, "text/plain", ""));
            return cors(newFixedLengthResponse(Response.Status.OK, "application/json", r.toString()));
        } catch (Exception e) {
            return cors(newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad request: " + e.getMessage()));
        }
    }

    // ---- JSON-RPC ---------------------------------------------------------------------------------

    private JSONObject handle(JSONObject req) throws JSONException {
        String method = req.optString("method", "");
        Object id = req.has("id") ? req.get("id") : null;
        JSONObject params = req.optJSONObject("params");
        if (params == null) params = new JSONObject();
        if (id == null) return null; // notification, e.g. notifications/initialized

        JSONObject resp = new JSONObject().put("jsonrpc", "2.0").put("id", id);
        try {
            switch (method) {
                case "initialize":
                    resp.put("result", new JSONObject()
                            .put("protocolVersion", params.optString("protocolVersion", "2025-03-26"))
                            .put("capabilities", new JSONObject().put("tools", new JSONObject()))
                            .put("serverInfo", new JSONObject().put("name", "mojo-launcher-mcp").put("version", "0.1.0"))
                            .put("instructions", "Controls the player's live Minecraft character inside Mojo Launcher. "
                                    + "The user can press Emergency Stop at any time; tools then return an error until they resume."));
                    break;
                case "ping":
                    resp.put("result", new JSONObject());
                    break;
                case "tools/list":
                    resp.put("result", new JSONObject().put("tools", McpTools.list()));
                    break;
                case "tools/call":
                    resp.put("result", McpTools.call(params.optString("name"), params.optJSONObject("arguments")));
                    break;
                default:
                    resp.put("error", new JSONObject().put("code", -32601).put("message", "Method not found: " + method));
            }
        } catch (Exception e) {
            resp.put("error", new JSONObject().put("code", -32603).put("message", String.valueOf(e.getMessage())));
        }
        return resp;
    }
}
