package git.artdeell.mojo.premods;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;

/** "Pre mods": one-tap install of recommended mods into the selected instance's mods folder. */
public class PreModsActivity extends AppCompatActivity {
    private static final String UA = "MojoLauncher-PreMods/1.0 (github.com/ankitgoelgopal-dotcom/Repo2)";
    private static final String MC_VERSION = "1.21.11";
    private static final String TELEMETRY_URL =
            "https://github.com/ankitgoelgopal-dotcom/Repo2/releases/download/mcp-telemetry/mcp-telemetry.jar";

    private static final class PreMod {
        final String title, description, filePrefix, fixedUrl, fixedName, modrinthProject;
        Button button;
        TextView status;

        PreMod(String title, String description, String filePrefix, String fixedUrl, String fixedName, String modrinthProject) {
            this.title = title;
            this.description = description;
            this.filePrefix = filePrefix;
            this.fixedUrl = fixedUrl;
            this.fixedName = fixedName;
            this.modrinthProject = modrinthProject;
        }
    }

    private final PreMod[] mods = {
            new PreMod("MCP Telemetry",
                    "Lets Claude read your coordinates, health and inventory (used by MCP Companion). Fabric " + MC_VERSION + ".",
                    "mcp-telemetry", TELEMETRY_URL, "mcp-telemetry.jar", null),
            new PreMod("Fabric API",
                    "Required library for Fabric mods, including MCP Telemetry. Downloaded from Modrinth for " + MC_VERSION + ".",
                    "fabric-api", null, null, "fabric-api"),
    };

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView targetView, warnView;
    private volatile File modsDir;
    private volatile String instanceName = "", instanceVersion = "";

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Pre mods");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("Pre mods", 22, true));
        root.addView(text("Tap Download and the mod is added to your currently selected instance automatically.", 14, false));
        targetView = text("Instance: loading...", 14, true);
        root.addView(targetView);
        warnView = text("", 13, false);
        warnView.setTextColor(0xFFD32F2F);
        warnView.setVisibility(View.GONE);
        root.addView(warnView);

        for (final PreMod m : mods) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(16), 0, dp(8));
            card.addView(text(m.title, 18, true));
            card.addView(text(m.description, 13, false));
            m.status = text("", 13, false);
            card.addView(m.status);
            m.button = new Button(this);
            m.button.setText("Download");
            m.button.setOnClickListener(v -> onButton(m));
            card.addView(m.button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(card);
        }
        root.addView(text("Tip: for these mods, make the selected instance a Fabric " + MC_VERSION + " instance.", 13, false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        io.execute(() -> {
            try {
                Instance inst = Instances.loadSelectedInstance();
                instanceName = inst.name;
                instanceVersion = String.valueOf(inst.versionId);
                modsDir = new File(inst.getGameDirectory(), "mods");
            } catch (Throwable t) {
                modsDir = null;
            }
            runOnUiThread(this::refresh);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private File installedFile(PreMod m) {
        File dir = modsDir;
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith(m.filePrefix) && n.endsWith(".jar")) return f;
        }
        return null;
    }

    private void refresh() {
        if (modsDir == null) {
            targetView.setText("Instance: none selected. Create or select an instance first.");
        } else {
            targetView.setText("Instance: " + instanceName + "\nMods folder: " + modsDir.getAbsolutePath());
        }
        String v = instanceVersion.toLowerCase(Locale.ROOT);
        boolean ok = v.contains("fabric") && v.contains(MC_VERSION);
        warnView.setVisibility(modsDir != null && !ok ? View.VISIBLE : View.GONE);
        warnView.setText("This instance (" + instanceVersion + ") does not look like Fabric " + MC_VERSION
                + ". The mods will be copied, but they will only load on a matching Fabric instance.");
        for (PreMod m : mods) {
            File f = installedFile(m);
            m.button.setEnabled(modsDir != null);
            m.button.setText(f != null ? "Remove" : "Download");
            m.status.setText(f != null ? "Installed: " + f.getName() : "Not installed");
        }
    }

    private void onButton(final PreMod m) {
        final File existing = installedFile(m);
        m.button.setEnabled(false);
        if (existing != null) {
            io.execute(() -> {
                boolean deleted = existing.delete();
                runOnUiThread(() -> {
                    if (!deleted) toast("Could not remove " + existing.getName());
                    refresh();
                });
            });
            return;
        }
        m.button.setText("Downloading...");
        io.execute(() -> {
            try {
                install(m);
                runOnUiThread(() -> toast(m.title + " installed"));
            } catch (Throwable t) {
                runOnUiThread(() -> toast("Download failed: " + t.getMessage()));
            }
            runOnUiThread(this::refresh);
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void install(PreMod m) throws Exception {
        File dir = modsDir;
        if (dir == null) throw new IllegalStateException("No instance selected");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + dir);
        String url = m.fixedUrl, name = m.fixedName;
        if (m.modrinthProject != null) {
            String q = "https://api.modrinth.com/v2/project/" + m.modrinthProject + "/version?game_versions="
                    + URLEncoder.encode("[\"" + MC_VERSION + "\"]", "UTF-8")
                    + "&loaders=" + URLEncoder.encode("[\"fabric\"]", "UTF-8");
            JSONArray versions = new JSONArray(readString(q));
            if (versions.length() == 0) throw new IllegalStateException("No " + m.title + " build for " + MC_VERSION);
            JSONArray files = versions.getJSONObject(0).getJSONArray("files");
            JSONObject pick = files.getJSONObject(0);
            for (int i = 0; i < files.length(); i++) {
                if (files.getJSONObject(i).optBoolean("primary")) pick = files.getJSONObject(i);
            }
            url = pick.getString("url");
            name = pick.getString("filename");
        }
        File part = new File(dir, name + ".part");
        try (InputStream in = new BufferedInputStream(open(url).getInputStream());
             OutputStream out = new FileOutputStream(part)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            part.delete();
            throw e;
        }
        if (part.length() < 1024) {
            part.delete();
            throw new IllegalStateException("Downloaded file is too small, the download link may be missing");
        }
        // Replace any older copy of the same mod only once the new one has fully downloaded.
        File old = installedFile(m);
        if (old != null) old.delete();
        File target = new File(dir, name);
        if (!part.renameTo(target)) throw new IllegalStateException("Could not save " + name);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code == 404) throw new IllegalStateException("File not found online yet (HTTP 404)");
        if (code >= 400) throw new IllegalStateException("Server returned HTTP " + code);
        return c;
    }

    private static String readString(String url) throws Exception {
        try (InputStream in = open(url).getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
}
