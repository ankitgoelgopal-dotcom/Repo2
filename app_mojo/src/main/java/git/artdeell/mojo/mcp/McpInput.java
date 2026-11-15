package git.artdeell.mojo.mcp;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import git.artdeell.mojo.LwjglGlfwKeycode;
import git.artdeell.mojo.game.platform.Platform;

/**
 * Injects keyboard / mouse input into the running game through the launcher's Platform layer.
 * Everything is posted to the main thread. Must be used from the ":game" process.
 */
public final class McpInput {
    private McpInput() {}

    /** Mouse pixels per degree at default sensitivity (Minecraft: 0.15 deg / px at 50%). Tweakable. */
    public static volatile double pixelsPerDegree = 6.67;
    private static final long MAX_HOLD_MS = 30_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Integer> KEYS = new HashMap<>();
    private static final Map<Integer, Runnable> pendingReleases = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static double estYaw = 0, estPitch = 0;
    private static volatile boolean antiAfk = false;

    static {
        KEYS.put("forward", KeyEvent.KEYCODE_W);
        KEYS.put("back", KeyEvent.KEYCODE_S);
        KEYS.put("left", KeyEvent.KEYCODE_A);
        KEYS.put("right", KeyEvent.KEYCODE_D);
        KEYS.put("jump", KeyEvent.KEYCODE_SPACE);
        KEYS.put("sneak", KeyEvent.KEYCODE_SHIFT_LEFT);
        KEYS.put("sprint", KeyEvent.KEYCODE_CTRL_LEFT);
        KEYS.put("inventory", KeyEvent.KEYCODE_E);
        KEYS.put("drop", KeyEvent.KEYCODE_Q);
        KEYS.put("swap_hands", KeyEvent.KEYCODE_F);
    }

    public static String[] actionNames() {
        return new String[]{"forward", "back", "left", "right", "jump", "sneak", "sprint",
                "attack", "use", "hotbar_slot", "inventory", "drop", "swap_hands"};
    }

    /** True once the game's platform backend is initialised (it's a Dummy before that). */
    public static boolean gameReady() {
        try {
            return Platform.PLATFORM != null
                    && !Platform.PLATFORM.getClass().getSimpleName().equals("DummyBackend");
        } catch (Throwable t) {
            return false;
        }
    }

    private static void key(int code, boolean down, int mods) {
        Platform.PLATFORM.sendKeyEvent(code, down, mods);
    }

    /** Perform an action. Returns a human readable result or throws IllegalArgumentException. */
    public static String perform(String action, long durationMs, int slot) {
        if (!gameReady()) throw new IllegalArgumentException("The game is not running yet.");
        final String a = action.toLowerCase(Locale.ROOT);
        final long dur = Math.max(0, Math.min(durationMs, MAX_HOLD_MS));
        switch (a) {
            case "attack":
                hold(-1, 0, dur);
                return "attack for " + dur + " ms";
            case "use":
                hold(-1, 1, dur);
                return "use for " + dur + " ms";
            case "hotbar_slot":
                if (slot < 1 || slot > 9) throw new IllegalArgumentException("slot must be 1-9");
                final int code = KeyEvent.KEYCODE_1 + (slot - 1);
                MAIN.post(() -> {
                    key(code, true, 0);
                    key(code, false, 0);
                });
                return "hotbar slot " + slot;
            default:
                Integer k = KEYS.get(a);
                if (k == null) throw new IllegalArgumentException("Unknown action: " + action);
                hold(k, -1, dur == 0 ? 120 : dur);
                return a + " for " + (dur == 0 ? 120 : dur) + " ms";
        }
    }

    /** Press a keyboard key (keyCode >= 0) or a mouse button (keyCode == -1, button given) for dur ms. */
    private static void hold(final int keyCode, final int mouseButton, long dur) {
        final int id = keyCode >= 0 ? keyCode : -2 - mouseButton;
        MAIN.post(() -> {
            Runnable old = pendingReleases.remove(id);
            if (old != null) MAIN.removeCallbacks(old);
            down(keyCode, mouseButton, true);
            Runnable up = () -> {
                pendingReleases.remove(id);
                down(keyCode, mouseButton, false);
            };
            pendingReleases.put(id, up);
            MAIN.postDelayed(up, dur == 0 ? 60 : dur);
        });
    }

    private static void down(int keyCode, int mouseButton, boolean isDown) {
        try {
            if (keyCode >= 0) key(keyCode, isDown, 0);
            else Platform.sendMouseButtonEvent(mouseButton == 0
                            ? MotionEvent.BUTTON_PRIMARY : MotionEvent.BUTTON_SECONDARY,
                    isDown ? 1 : 0, 0);
        } catch (Throwable ignored) {
        }
    }

    /** Relative camera movement in degrees. Positive yaw = turn right, positive pitch = look down. */
    public static void lookBy(final double dYaw, final double dPitch) {
        if (!gameReady()) throw new IllegalArgumentException("The game is not running yet.");
        estYaw = ((estYaw + dYaw) % 360 + 360) % 360;
        estPitch = Math.max(-90, Math.min(90, estPitch + dPitch));
        MAIN.post(() -> {
            // Move in small steps so the game registers a smooth turn instead of one huge jump.
            int steps = (int) Math.max(1, Math.min(30, Math.hypot(dYaw, dPitch) / 3));
            for (int i = 0; i < steps; i++) {
                final double sx = dYaw * pixelsPerDegree / steps, sy = dPitch * pixelsPerDegree / steps;
                MAIN.postDelayed(() -> {
                    Platform.cursorX += sx;
                    Platform.cursorY += sy;
                    Platform.sendCursorPosition();
                }, i * 15L);
            }
        });
    }

    /**
     * Absolute look. The launcher cannot read the game's real camera angles, so this is relative to the
     * angles Claude has commanded so far (starting at yaw 0 / pitch 0 = wherever the player was facing).
     */
    public static void lookAt(double yaw, double pitch) {
        double dy = ((yaw - estYaw + 540) % 360) - 180;
        lookBy(dy, pitch - estPitch);
    }

    public static void resetLookEstimate() {
        estYaw = 0;
        estPitch = 0;
    }

    /** Type a chat message (or a /command). Best effort for ASCII text. */
    public static void chat(final String text) {
        if (!gameReady()) throw new IllegalArgumentException("The game is not running yet.");
        final boolean command = text.startsWith("/");
        final String body = command ? text.substring(1) : text;
        MAIN.post(() -> {
            // T opens chat; "/" opens chat with a slash already typed.
            int open = command ? KeyEvent.KEYCODE_SLASH : KeyEvent.KEYCODE_T;
            key(open, true, 0);
            key(open, false, 0);
            MAIN.postDelayed(() -> typeText(body), 250);
        });
    }

    private static void typeText(String body) {
        KeyCharacterMap map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        KeyEvent[] events = map.getEvents(body.toCharArray());
        if (events != null) {
            for (KeyEvent ev : events) {
                int mods = ev.isShiftPressed() ? LwjglGlfwKeycode.GLFW_MOD_SHIFT : 0;
                try {
                    key(ev.getKeyCode(), ev.getAction() == KeyEvent.ACTION_DOWN, mods);
                } catch (Throwable ignored) {
                }
            }
        }
        MAIN.postDelayed(() -> {
            key(KeyEvent.KEYCODE_ENTER, true, 0);
            key(KeyEvent.KEYCODE_ENTER, false, 0);
        }, 150);
    }

    /** Release every held key / mouse button. Safe to call from any thread, any time. */
    public static void releaseAll() {
        McpNav.cancel();
        MAIN.post(() -> {
            for (Runnable r : pendingReleases.values()) {
                MAIN.removeCallbacks(r);
                r.run();
            }
            pendingReleases.clear();
            if (gameReady()) {
                for (int k : KEYS.values()) down(k, -1, false);
                down(-1, 0, false);
                down(-1, 1, false);
            }
        });
    }

    /** Release only movement keys (used when navigation ends). Does not cancel navigation. */
    static void releaseMovement() {
        MAIN.post(() -> {
            for (String n : new String[]{"forward", "sprint", "jump", "left", "right", "back"}) {
                Integer k = KEYS.get(n);
                if (k == null) continue;
                Runnable r = pendingReleases.remove(k);
                if (r != null) MAIN.removeCallbacks(r);
                if (gameReady()) down(k, -1, false);
            }
        });
    }

    // ---- anti-AFK -------------------------------------------------------------------------------

    private static final Runnable AFK_TICK = new Runnable() {
        @Override
        public void run() {
            if (!antiAfk) return;
            try {
                if (gameReady() && McpState.running && !McpState.paused) {
                    int roll = RANDOM.nextInt(4);
                    if (roll == 0) lookBy(RANDOM.nextInt(31) - 15, RANDOM.nextInt(11) - 5);
                    else if (roll == 1) perform("jump", 150, 0);
                    else if (roll == 2) perform(RANDOM.nextBoolean() ? "left" : "right", 250 + RANDOM.nextInt(300), 0);
                    else perform("sneak", 300 + RANDOM.nextInt(400), 0);
                }
            } catch (Throwable ignored) {
            }
            MAIN.postDelayed(this, 20_000 + RANDOM.nextInt(25_000));
        }
    };

    public static void setAntiAfk(boolean enabled) {
        if (enabled == antiAfk) return;
        antiAfk = enabled;
        MAIN.removeCallbacks(AFK_TICK);
        if (enabled) MAIN.postDelayed(AFK_TICK, 5_000);
    }

    public static boolean isAntiAfk() {
        return antiAfk;
    }
}
