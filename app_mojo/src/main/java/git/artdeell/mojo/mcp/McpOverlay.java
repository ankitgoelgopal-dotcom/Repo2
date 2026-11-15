package git.artdeell.mojo.mcp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * In-game quick toggle: a small button that is visible only while the MCP server is running.
 * Tap = Emergency Stop (releases all keys and pauses Claude); tap again = resume.
 */
public final class McpOverlay {
    private McpOverlay() {}

    public static void attach(final Activity activity) {
        final ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        final float d = activity.getResources().getDisplayMetrics().density;
        final TextView btn = new TextView(activity);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));
        btn.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (4 * d);
        content.addView(btn, lp);

        btn.setOnClickListener(v -> {
            Context c = v.getContext();
            McpInput.releaseAll();
            c.startService(new Intent(c, McpCompanionService.class).setAction(McpState.ACTION_TOGGLE_PAUSE));
        });

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                if (!btn.isAttachedToWindow() && btn.getParent() == null) return;
                boolean show = McpState.running;
                btn.setVisibility(show ? View.VISIBLE : View.GONE);
                if (show) {
                    boolean p = McpState.paused;
                    btn.setText(p ? "Claude PAUSED - tap to resume" : "Claude active - tap = EMERGENCY STOP");
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(12 * d);
                    bg.setColor(p ? 0xCC8A6D00 : 0xCCB00020);
                    btn.setBackground(bg);
                }
                btn.postDelayed(this, 700);
            }
        };
        btn.post(refresh);
    }
}
