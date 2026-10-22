package id.mchost.desktop;

import id.mchost.core.Host;
import id.mchost.core.IO;
import id.mchost.core.LogBuffer;
import id.mchost.core.Platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Platform for the Windows app: data under %LOCALAPPDATA%\MCHost, Temurin JREs downloaded the first time they are needed. */
final class WindowsPlatform implements Platform {
    private static final int[] JAVAS = {17, 21, 25};
    private final File base;
    volatile Consumer<String> summary = s -> { };
    volatile BiConsumer<String, String> notifier = (title, text) -> { };

    WindowsPlatform() {
        String local = System.getenv("LOCALAPPDATA");
        base = new File(local != null ? local : System.getProperty("user.home"), "MCHost");
        base.mkdirs();
    }

    private File runtime() {
        return new File(base, "runtime");
    }

    /** Smallest Java major we ship that runs {@code want}, else the newest. */
    private static int pick(int want) {
        for (int v : JAVAS) if (v >= want) return v;
        return JAVAS[JAVAS.length - 1];
    }

    @Override
    public File baseDir() {
        return base;
    }

    @Override
    public File javaBinary(int want) {
        File java = new File(runtime(), "jre" + pick(want) + "/bin/java.exe");
        return java.isFile() ? java : null;
    }

    @Override
    public void prepareJava(int want, LogBuffer log) throws IOException {
        int v = pick(want);
        File dir = new File(runtime(), "jre" + v);
        if (new File(dir, "bin/java.exe").isFile()) return;
        File zip = new File(runtime(), "jre" + v + ".zip"), tmp = new File(runtime(), "jre" + v + ".tmp");
        IO.deleteRecursive(tmp);
        runtime().mkdirs();
        log.panel(Host.tr("Downloading Java " + v + " (once, about 45 MB)...", "Mengunduh Java " + v + " (sekali saja, sekitar 45 MB)..."));
        try {
            IO.download("https://api.adoptium.net/v3/binary/latest/" + v + "/ga/windows/x64/jre/hotspot/normal/eclipse", zip, log);
            IO.unzipAll(zip, tmp, n -> { });
            File[] top = tmp.listFiles(File::isDirectory); // the zip holds one folder, e.g. jdk-21.0.5+11-jre
            if (top == null || top.length != 1 || !new File(top[0], "bin/java.exe").isFile() || !top[0].renameTo(dir)) {
                throw new IOException("Java " + v + ": " + Host.tr("unexpected download", "isi unduhan tidak dikenali"));
            }
        } finally {
            IO.deleteRecursive(tmp);
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
        log.panel("Java " + v + Host.tr(" is ready.", " siap."));
    }

    @Override
    public Map<String, String> childEnv(File java) {
        return new HashMap<>();
    }

    @Override
    public File playitBinary() {
        File p = new File(runtime(), "playit.exe");
        return p.isFile() ? p : null;
    }

    @Override
    public void prepareRuntime(LogBuffer log) {
    }

    @Override
    public InputStream openAsset(String path) {
        return WindowsPlatform.class.getResourceAsStream("/panel/" + path);
    }

    @Override
    public String batteryJson() {
        return "null";
    }

    @Override
    public void onSummary(String s) {
        summary.accept(s);
    }

    @Override
    public void notify(String title, String text) {
        notifier.accept(title, text);
    }

    @Override
    public String name() {
        return "windows";
    }

    @Override
    public long processCpuMs(int pid) {
        if (pid <= 0) return -1;
        return ProcessHandle.of(pid).flatMap(h -> h.info().totalCpuDuration()).map(Duration::toMillis).orElse(-1L);
    }

    /** Working set from tasklist; its CSV ends with the memory column, e.g. "1.234.567 K" (separator depends on locale). */
    @Override
    public long processRssMb(int pid) {
        if (pid <= 0) return -1;
        try {
            Process t = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH").redirectErrorStream(true).start();
            String out = new String(t.getInputStream().readAllBytes()).trim();
            t.waitFor();
            String[] f = out.split("\",\"");
            if (f.length < 5) return -1;
            String kb = f[f.length - 1].replaceAll("[^0-9]", "");
            return kb.isEmpty() ? -1 : Long.parseLong(kb) / 1024;
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public long totalRamMb() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / (1024 * 1024);
    }
}
