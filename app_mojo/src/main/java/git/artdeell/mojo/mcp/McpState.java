package git.artdeell.mojo.mcp;

/**
 * Shared state of the MCP companion. Lives in the ":game" process, because that is where the
 * game's input bridge lives. The launcher UI (":launcher" process) only talks to it through intents
 * and broadcasts defined here.
 */
public final class McpState {
    private McpState() {}

    public static final String ACTION_START = "git.artdeell.mojo.mcp.START";
    public static final String ACTION_STOP = "git.artdeell.mojo.mcp.STOP";
    public static final String ACTION_QUERY = "git.artdeell.mojo.mcp.QUERY";
    public static final String ACTION_TOGGLE_PAUSE = "git.artdeell.mojo.mcp.TOGGLE_PAUSE";
    public static final String ACTION_SET_MODE = "git.artdeell.mojo.mcp.SET_MODE";
    public static final String ACTION_STATUS = "git.artdeell.mojo.mcp.STATUS";

    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_PAUSED = "paused";
    public static final String EXTRA_ERROR = "error";

    public static final String STATUS_STOPPED = "Stopped";
    public static final String STATUS_STARTING = "Starting Tunnel...";
    public static final String STATUS_READY = "Ready";
    public static final String STATUS_CONNECTED = "Connected";

    public static final String MODE_TAKEOVER = "takeover_player";
    public static final String MODE_COMPANION = "companion_bot";

    public static final int LOCAL_PORT = 3000;

    public static volatile boolean running = false;
    /** Set by the Emergency Stop button. While true, every control tool call is refused. */
    public static volatile boolean paused = false;
    public static volatile String status = STATUS_STOPPED;
    public static volatile String url = "";
    public static volatile String mode = MODE_TAKEOVER;
    public static volatile String error = "";
}
