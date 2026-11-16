package git.artdeell.mojo.mcp;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the bundled cloudflared binary and captures the generated https://*.trycloudflare.com URL. */
public class McpTunnel {
    public interface Listener {
        void onUrl(String baseUrl);
        void onExit(String reason);
    }

    private static final Pattern URL = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");

    private volatile Process process;
    private volatile boolean stopping = false;

    public synchronized void start(Context context, int port, final Listener listener) throws Exception {
        File bin = new File(context.getApplicationInfo().nativeLibraryDir, "libcloudflared.so");
        if (!bin.exists()) {
            throw new IllegalStateException("libcloudflared.so is not bundled in this build "
                    + "(the CI step that downloads it must have failed).");
        }
        stopping = false;
        ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath(), "tunnel", "--no-autoupdate",
                "--url", "http://127.0.0.1:" + port);
        pb.redirectErrorStream(true); // cloudflared logs to stderr; merging keeps parsing simple
        pb.environment().put("HOME", context.getFilesDir().getAbsolutePath());
        // Go on Android has neither /etc/resolv.conf nor the usual CA folders: hand both over explicitly.
        pb.environment().put("MCP_DNS", phoneDnsServers(context));
        pb.environment().put("SSL_CERT_DIR", "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts");
        pb.environment().put("TMPDIR", context.getCacheDir().getAbsolutePath());
        final Process p = pb.start();
        process = p;
        Thread t = new Thread(() -> {
            final ArrayDeque<String> tail = new ArrayDeque<>();
            boolean found = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    tail.addLast(line);
                    if (tail.size() > 6) tail.removeFirst();
                    if (!found) {
                        Matcher m = URL.matcher(line);
                        if (m.find()) {
                            found = true;
                            listener.onUrl(m.group());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            if (!stopping) {
                StringBuilder sb = new StringBuilder("cloudflared exited");
                for (String s : tail) sb.append('\n').append(s);
                listener.onExit(sb.toString());
            }
        }, "mcp-cloudflared");
        t.setDaemon(true);
        t.start();
    }

    /** The phone's current IPv4 DNS servers (comma separated), or "" if unknown. */
    private static String phoneDnsServers(Context context) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.Network n = cm.getActiveNetwork();
            android.net.LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp == null) return "";
            StringBuilder sb = new StringBuilder();
            for (java.net.InetAddress a : lp.getDnsServers()) {
                if (!(a instanceof java.net.Inet4Address) || a.isLoopbackAddress()) continue;
                if (sb.length() > 0) sb.append(',');
                sb.append(a.getHostAddress());
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public synchronized void stop() {
        stopping = true;
        Process p = process;
        process = null;
        if (p != null) p.destroy();
    }
}
