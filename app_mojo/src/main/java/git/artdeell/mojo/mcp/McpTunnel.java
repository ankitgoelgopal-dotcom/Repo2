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

    public synchronized void stop() {
        stopping = true;
        Process p = process;
        process = null;
        if (p != null) p.destroy();
    }
}
