package git.artdeell.mojo.mcp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;

/** Foreground service (":game" process) that hosts the MCP server and the public tunnel. */
public class McpCompanionService extends Service {
    private static final int NOTIFICATION_ID = 7771;
    private final Handler main = new Handler(Looper.getMainLooper());
    private McpServer server;
    private McpTunnel tunnel;
    private long lastActivity = 0;

    @Override
    public void onCreate() {
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (McpState.ACTION_START.equals(action)) {
            startNotification();
            start(intent.getStringExtra(McpState.EXTRA_TOKEN));
        } else if (McpState.ACTION_TOGGLE_PAUSE.equals(action)) {
            McpState.paused = !McpState.paused;
            McpInput.releaseAll();
            if (McpState.running) startNotification();
            broadcast();
        } else if (McpState.ACTION_STOP.equals(action)) {
            teardown();
            stopForeground(true);
            stopSelf();
        } else { // QUERY or null
            broadcast();
            if (!McpState.running) stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startNotification() {
        PendingIntent pause = PendingIntent.getService(this, 1,
                new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_TOGGLE_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, McpCompanionService.class).setAction(McpState.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, "channel_id")
                .setContentTitle("MCP Companion")
                .setContentText(McpState.paused ? "Claude is PAUSED (emergency stop)" : "Claude can control the game")
                .addAction(android.R.drawable.ic_media_pause, McpState.paused ? "Resume Claude" : "Emergency Stop", pause)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop server", stop)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .setNotificationSilent()
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void start(String token) {
        if (McpState.running) {
            broadcast();
            return;
        }
        McpState.error = "";
        McpState.url = "";
        McpState.running = true;
        McpState.status = McpState.STATUS_STARTING;
        broadcast();
        try {
            server = new McpServer(McpState.LOCAL_PORT, token, () -> {
                lastActivity = System.currentTimeMillis();
                if (!McpState.STATUS_CONNECTED.equals(McpState.status) && McpState.running) {
                    McpState.status = McpState.STATUS_CONNECTED;
                    main.post(this::broadcast);
                }
            });
            server.start(15_000, false);
            tunnel = new McpTunnel();
            final String t = token;
            tunnel.start(this, McpState.LOCAL_PORT, new McpTunnel.Listener() {
                @Override
                public void onUrl(String baseUrl) {
                    McpState.url = baseUrl + "/" + t + "/mcp";
                    if (!McpState.STATUS_CONNECTED.equals(McpState.status)) McpState.status = McpState.STATUS_READY;
                    main.post(McpCompanionService.this::broadcast);
                }

                @Override
                public void onExit(String reason) {
                    main.post(() -> fail(reason));
                }
            });
        } catch (Throwable e) {
            fail(String.valueOf(e.getMessage()));
        }
    }

    private void fail(String reason) {
        McpState.error = reason;
        teardown();
        broadcast();
        stopForeground(true);
        stopSelf();
    }

    private void teardown() {
        McpInput.releaseAll();
        McpInput.setAntiAfk(false);
        if (tunnel != null) tunnel.stop();
        tunnel = null;
        if (server != null) server.stop();
        server = null;
        McpState.running = false;
        McpState.paused = false;
        McpState.status = McpState.STATUS_STOPPED;
        McpState.url = "";
        broadcast();
    }

    private void broadcast() {
        Intent i = new Intent(McpState.ACTION_STATUS).setPackage(getPackageName())
                .putExtra(McpState.EXTRA_STATUS, McpState.status)
                .putExtra(McpState.EXTRA_URL, McpState.url)
                .putExtra(McpState.EXTRA_PAUSED, McpState.paused)
                .putExtra(McpState.EXTRA_ERROR, McpState.error);
        sendBroadcast(i);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        teardown();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        teardown();
    }
}
