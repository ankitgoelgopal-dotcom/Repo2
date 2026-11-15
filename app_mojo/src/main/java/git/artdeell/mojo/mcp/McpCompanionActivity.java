package git.artdeell.mojo.mcp;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import java.security.SecureRandom;

/** "MCP Companion" screen (launcher process). Controls the service that runs in the game process. */
public class McpCompanionActivity extends AppCompatActivity {
    private static final String PREFS = "mcp_companion";
    private SharedPreferences prefs;
    private SwitchCompat toggle;
    private TextView statusView, errorView;
    private EditText urlField;
    private Button copyButton;
    private boolean updating = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            show(i.getStringExtra(McpState.EXTRA_STATUS), i.getStringExtra(McpState.EXTRA_URL),
                    i.getBooleanExtra(McpState.EXTRA_PAUSED, false), i.getStringExtra(McpState.EXTRA_ERROR));
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("MCP Companion");
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("MCP Companion", 22, true));
        root.addView(text("Let Claude play your live Minecraft character through a Custom Connector.", 14, false));

        root.addView(text("Control mode", 16, true));
        RadioGroup group = new RadioGroup(this);
        RadioButton modeA = new RadioButton(this);
        modeA.setText("Mode A: Companion Bot (separate account) - coming later");
        modeA.setEnabled(false);
        RadioButton modeB = new RadioButton(this);
        modeB.setText("Mode B: Take over my character (live control)");
        modeB.setId(View.generateViewId());
        modeB.setChecked(true);
        group.addView(modeA);
        group.addView(modeB);
        root.addView(group);

        toggle = new SwitchCompat(this);
        toggle.setText("Enable MCP Server");
        toggle.setPadding(0, dp(12), 0, dp(12));
        root.addView(toggle);

        statusView = text("Status: " + McpState.STATUS_STOPPED, 16, true);
        root.addView(statusView);
        errorView = text("", 13, false);
        errorView.setTextColor(0xFFD32F2F);
        errorView.setVisibility(View.GONE);
        root.addView(errorView);

        urlField = new EditText(this);
        urlField.setInputType(InputType.TYPE_NULL);
        urlField.setTextIsSelectable(true);
        urlField.setKeyListener(null);
        urlField.setHint("The public HTTPS URL will appear here");
        urlField.setTextSize(13);
        root.addView(urlField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        copyButton = new Button(this);
        copyButton.setText("Copy URL");
        copyButton.setEnabled(false);
        copyButton.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("MCP URL", urlField.getText().toString()));
            Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show();
        });
        root.addView(copyButton);

        Button pause = new Button(this);
        pause.setText("Emergency Stop / Resume Claude");
        pause.setOnClickListener(v -> startService(new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_TOGGLE_PAUSE)));
        root.addView(pause);

        Button regen = new Button(this);
        regen.setText("Generate new secret URL");
        regen.setOnClickListener(v -> {
            prefs.edit().remove("token").apply();
            if (toggle.isChecked()) toggle.setChecked(false);
            Toast.makeText(this, "New secret will be used next time you enable the server", Toast.LENGTH_LONG).show();
        });
        root.addView(regen);

        TextView warn = text("Security: anyone with this URL can control your game. Never share it publicly, and "
                + "generate a new one if it leaks. Use Emergency Stop (also in the game and in the notification) to take back control.", 13, false);
        warn.setTextColor(0xFFD32F2F);
        root.addView(warn);
        root.addView(text("Add it in Claude: Settings > Connectors > Add custom connector, and paste the URL. "
                + "Control only works while a game is running.", 13, false));

        toggle.setOnCheckedChangeListener((b, checked) -> {
            if (updating) return;
            if (checked) {
                Intent i = new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_START)
                        .putExtra(McpState.EXTRA_TOKEN, token()).putExtra(McpState.EXTRA_MODE, McpState.MODE_TAKEOVER);
                ContextCompat.startForegroundService(this, i);
                show(McpState.STATUS_STARTING, "", false, "");
            } else {
                startService(new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_STOP));
                show(McpState.STATUS_STOPPED, "", false, "");
            }
        });
    }

    private String token() {
        String t = prefs.getString("token", null);
        if (t == null) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            t = sb.toString();
            prefs.edit().putString("token", t).apply();
        }
        return t;
    }

    private void show(String status, String url, boolean paused, String error) {
        updating = true;
        boolean on = status != null && !status.equals(McpState.STATUS_STOPPED);
        toggle.setChecked(on);
        updating = false;
        statusView.setText("Status: " + (status == null ? McpState.STATUS_STOPPED : status) + (paused && on ? "  (PAUSED)" : ""));
        urlField.setText(url == null ? "" : url);
        copyButton.setEnabled(url != null && !url.isEmpty());
        boolean hasErr = error != null && !error.isEmpty();
        errorView.setText(hasErr ? "Error: " + error : "");
        errorView.setVisibility(hasErr ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(this, receiver, new IntentFilter(McpState.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
        startService(new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_QUERY));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }
}
