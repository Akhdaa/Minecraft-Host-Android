package id.mchost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.IOException;

import id.mchost.core.Config;
import id.mchost.core.Host;
import id.mchost.core.MiniHttp;
import id.mchost.core.PanelApi;

/** Foreground service that keeps the panel, the Minecraft server and the tunnel alive. */
public class ServerService extends Service {
    public static final String ACTION_SHUTDOWN = "id.mchost.SHUTDOWN";
    private static final String CHANNEL = "server";
    private static final int NOTIF_ID = 25565;

    private static volatile Host host;
    private static volatile boolean panelUp;

    private MiniHttp http;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private NotificationManager nm;
    private String lastSummary = "Menyiapkan panel...";

    public static Host host() {
        return host;
    }

    public static boolean panelUp() {
        return panelUp;
    }

    public static int panelPort(Context ctx) {
        Host h = host;
        if (h != null) return h.cfg.getInt(Config.PORT, 8080);
        return new Config(new java.io.File(ctx.getFilesDir(), "panel.properties")).getInt(Config.PORT, 8080);
    }

    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ServerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Server Minecraft", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        startForeground(NOTIF_ID, buildNotification(lastSummary));

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mchost:server");
        wake.setReferenceCounted(false);
        wake.acquire();
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mchost:wifi");
            wifi.setReferenceCounted(false);
            wifi.acquire();
        }

        Host h = new Host(new AndroidPlatform(this, this::updateNotification));
        host = h;
        http = new MiniHttp(new PanelApi(h));
        int port = h.cfg.getInt(Config.PORT, 8080);
        try {
            http.start(port);
            panelUp = true;
        } catch (IOException e) {
            h.mcLog.panel("Port panel " + port + " dipakai aplikasi lain: " + e.getMessage());
            updateNotification("Gagal membuka port panel " + port);
        }
        h.init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHUTDOWN.equals(intent.getAction())) {
            updateNotification("Mematikan server...");
            new Thread(() -> {
                Host h = host;
                if (h != null) h.shutdown(60_000);
                stopForeground(true);
                stopSelf();
            }, "shutdown").start();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        panelUp = false;
        Host h = host;
        if (h != null && h.mc.alive()) h.mc.kill();
        if (h != null) h.playit.stop();
        host = null;
        if (http != null) http.stop();
        if (wake != null && wake.isHeld()) wake.release();
        if (wifi != null && wifi.isHeld()) wifi.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification(String summary) {
        lastSummary = summary;
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(summary));
    }

    private Notification buildNotification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent shut = PendingIntent.getService(this, 1, new Intent(this, ServerService.class).setAction(ACTION_SHUTDOWN),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("MC Host")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Matikan semua", shut).build())
                .build();
    }
}
