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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import id.akhmad.mchost.core.IO;
import id.akhmad.mchost.core.Json;
import id.akhmad.mchost.core.LogBuffer;
import id.akhmad.mchost.core.Platform;

/**
 * Android implementation: Termux-built OpenJDKs (assets/runtime/jreNN.bin, one per Java major) extracted on first
 * use to files/runtime/jreNN/, plus playitd.
 */
public class AndroidPlatform implements Platform {
    public interface SummaryListener {
        void onSummary(String s);
    }

    private final Context ctx;
    private final File runtimeDir;
    private final SummaryListener listener;
    private volatile boolean ready;
    private List<Integer> bundled;

    public AndroidPlatform(Context ctx, SummaryListener listener) {
        this.ctx = ctx.getApplicationContext();
        this.runtimeDir = new File(this.ctx.getFilesDir(), "runtime");
        this.listener = listener;
    }

    @Override
    public File baseDir() {
        return ctx.getFilesDir();
    }

    /** Java majors this APK carries, ascending (from asset names jre17.bin, jre21.bin, ...). */
    private synchronized List<Integer> bundled() {
        if (bundled != null) return bundled;
        List<Integer> out = new ArrayList<>();
        try {
            String[] names = ctx.getAssets().list("runtime");
            if (names != null) for (String n : names) {
                if (n.matches("jre\\d+\\.bin")) out.add(Integer.parseInt(n.substring(3, n.length() - 4)));
            }
        } catch (IOException ignored) {
        }
        Collections.sort(out);
        return bundled = out;
    }

    /** Smallest bundled major >= want, else the newest one (old Minecraft runs on newer Java); -1 if none. */
    private int pick(int want) {
        List<Integer> b = bundled();
        for (int m : b) if (m >= want) return m;
        return b.isEmpty() ? -1 : b.get(b.size() - 1);
    }

    private File jreDir(int major) {
        return new File(runtimeDir, "jre" + major);
    }

    private String assetText(String path) throws IOException {
        try (InputStream in = ctx.getAssets().open(path)) {
            return new String(IO.readAll(in, 4096), StandardCharsets.UTF_8).trim();
        }
    }

    private boolean extracted(int major) {
        File marker = new File(jreDir(major), ".installed");
        try {
            return marker.exists() && IO.readText(marker).trim().equals(assetText("runtime/jre" + major + ".version"))
                    && new File(jreDir(major), "jvm/bin/java").exists();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public File javaBinary(int wantMajor) {
        int m = pick(wantMajor);
        return m > 0 && extracted(m) ? new File(jreDir(m), "jvm/bin/java") : null;
    }

    @Override
    public synchronized void prepareJava(int wantMajor, LogBuffer log) throws IOException {
        int m = pick(wantMajor);
        if (m < 0) throw new IOException("APK ini tidak membawa Java runtime (build CI tidak lengkap)");
        if (extracted(m)) return;
        String version = assetText("runtime/jre" + m + ".version");
        File dir = jreDir(m);
        log.panel("Ekstrak Java " + m + " (" + version + "), sekali saja...");
        IO.deleteRecursive(dir);
        dir.mkdirs();
        int n;
        try (InputStream in = ctx.getAssets().open("runtime/jre" + m + ".bin", AssetManager.ACCESS_STREAMING)) {
            n = IO.extractTarGz(in, dir);
        }
        if (!new File(dir, "jvm/bin/java").exists()) throw new IOException("jvm/bin/java tidak ditemukan setelah ekstrak");
        // Make every file in bin/ executable even if the archive lost the mode bits.
        File[] bins = new File(dir, "jvm/bin").listFiles();
        if (bins != null) for (File b : bins) //noinspection ResultOfMethodCallIgnored
            b.setExecutable(true, false);
        File helper = new File(dir, "jvm/lib/jspawnhelper");
        if (helper.exists()) //noinspection ResultOfMethodCallIgnored
            helper.setExecutable(true, false);
        IO.writeText(new File(dir, ".installed"), version);
        log.panel("Java " + m + " terpasang (" + n + " file).");
    }

    @Override
    public Map<String, String> childEnv(File java) {
        Map<String, String> env = new HashMap<>();
        String path = "/system/bin:/system/xbin";
        if (java != null) {
            File jvm = java.getParentFile().getParentFile(), root = jvm.getParentFile();
            String j = jvm.getAbsolutePath();
            env.put("JAVA_HOME", j);
            env.put("LD_LIBRARY_PATH", new File(root, "lib").getAbsolutePath() + ":" + j + "/lib:" + j + "/lib/server");
            path = j + "/bin:" + path;
        }
        File home = new File(ctx.getFilesDir(), "home");
        home.mkdirs();
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
        env.put("PATH", path);
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
        runtimeDir.mkdirs();
        // single-Java builds extracted straight into runtime/; that copy is unused now
        for (String old : new String[]{"jvm", "lib", ".installed"}) IO.deleteRecursive(new File(runtimeDir, old));
        if (bundled().isEmpty()) throw new IOException("APK ini tidak membawa Java runtime (build CI tidak lengkap)");
        String[] names = ctx.getAssets().list("runtime");
        File out = new File(runtimeDir, "playitd");
        if (names == null || !Arrays.asList(names).contains("playitd")) {
            log.panel("playitd tidak ada di APK ini, fitur tunnel nonaktif.");
        } else {
            // ponytail: copied on every launch (a few MB) instead of tracking the APK version
            try (InputStream in = ctx.getAssets().open("runtime/playitd", AssetManager.ACCESS_STREAMING);
                 OutputStream os = new FileOutputStream(out)) {
                IO.copy(in, os);
            } catch (IOException e) {
                if (!out.exists()) throw e;
                log.panel("playitd lama dipakai (" + e.getMessage() + ")"); // e.g. a leftover playitd still running
            }
            //noinspection ResultOfMethodCallIgnored
            out.setExecutable(true, false);
        }
        ready = true;
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
