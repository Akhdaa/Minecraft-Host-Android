package id.akhmad.mchost;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.os.BatteryManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import id.akhmad.mchost.core.IO;
import id.akhmad.mchost.core.Json;
import id.akhmad.mchost.core.LogBuffer;
import id.akhmad.mchost.core.Platform;

/** Android implementation: bundled Termux-built OpenJDK + playitd extracted from APK assets. */
public class AndroidPlatform implements Platform {
    public interface SummaryListener {
        void onSummary(String s);
    }

    private final Context ctx;
    private final File runtimeDir;
    private final SummaryListener listener;
    private volatile boolean ready;

    public AndroidPlatform(Context ctx, SummaryListener listener) {
        this.ctx = ctx.getApplicationContext();
        this.runtimeDir = new File(this.ctx.getFilesDir(), "runtime");
        this.listener = listener;
    }

    @Override
    public File baseDir() {
        return ctx.getFilesDir();
    }

    @Override
    public File javaBinary() {
        return ready ? new File(runtimeDir, "jvm/bin/java") : null;
    }

    @Override
    public Map<String, String> childEnv() {
        Map<String, String> env = new HashMap<>();
        String jvm = new File(runtimeDir, "jvm").getAbsolutePath();
        env.put("JAVA_HOME", jvm);
        env.put("LD_LIBRARY_PATH", new File(runtimeDir, "lib").getAbsolutePath() + ":" + jvm + "/lib:" + jvm + "/lib/server");
        File home = new File(ctx.getFilesDir(), "home");
        home.mkdirs();
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
        env.put("PATH", jvm + "/bin:/system/bin:/system/xbin");
        env.put("LANG", "en_US.UTF-8");
        return env;
    }

    @Override
    public File playitBinary() {
        File f = new File(runtimeDir, "playitd");
        return ready && f.exists() ? f : null;
    }

    @Override
    public void prepareRuntime(LogBuffer log) throws IOException {
        AssetManager am = ctx.getAssets();
        String version;
        try (InputStream in = am.open("runtime/version")) {
            version = new String(IO.readAll(in, 4096), StandardCharsets.UTF_8).trim();
        } catch (FileNotFoundException e) {
            throw new IOException("APK ini tidak membawa Java runtime (build CI tidak lengkap)");
        }
        File marker = new File(runtimeDir, ".installed");
        if (marker.exists() && IO.readText(marker).trim().equals(version) && new File(runtimeDir, "jvm/bin/java").exists()) {
            ready = true;
            return;
        }
        log.panel("Ekstrak Java runtime " + version + " (sekali saja)...");
        IO.deleteRecursive(runtimeDir);
        runtimeDir.mkdirs();
        int n;
        try (InputStream in = am.open("runtime/jre.bin", AssetManager.ACCESS_STREAMING)) {
            n = IO.extractTarGz(in, runtimeDir);
        }
        try (InputStream in = am.open("runtime/playitd", AssetManager.ACCESS_STREAMING)) {
            File out = new File(runtimeDir, "playitd");
            try (OutputStream os = new FileOutputStream(out)) {
                IO.copy(in, os);
            }
            //noinspection ResultOfMethodCallIgnored
            out.setExecutable(true, false);
        } catch (FileNotFoundException ignored) {
            log.panel("playitd tidak ada di APK ini, fitur tunnel nonaktif.");
        }
        File java = new File(runtimeDir, "jvm/bin/java");
        if (!java.exists()) throw new IOException("jvm/bin/java tidak ditemukan setelah ekstrak");
        // Make every file in bin/ executable even if the archive lost the mode bits.
        File[] bins = new File(runtimeDir, "jvm/bin").listFiles();
        if (bins != null) for (File b : bins) //noinspection ResultOfMethodCallIgnored
            b.setExecutable(true, false);
        File helper = new File(runtimeDir, "jvm/lib/jspawnhelper");
        if (helper.exists()) //noinspection ResultOfMethodCallIgnored
            helper.setExecutable(true, false);
        IO.writeText(marker, version);
        ready = true;
        log.panel("Runtime terpasang (" + n + " file).");
    }

    @Override
    public InputStream openAsset(String path) throws IOException {
        try {
            return ctx.getAssets().open("panel/" + path);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String batteryJson() {
        Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return "null";
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        return Json.obj()
                .put("level", scale > 0 ? Math.round(level * 100f / scale) : level)
                .put("temp", temp / 10.0)
                .put("charging", charging)
                .toString();
    }

    @Override
    public void onSummary(String summary) {
        if (listener != null) listener.onSummary(summary);
    }
}
