package id.mchost.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Everything the core needs from the host OS (Android app, or a desktop JVM for testing). */
public interface Platform {
    /** App-private base directory (servers/, backups/, runtime/ live under it). */
    File baseDir();

    /**
     * Java executable that can run {@code wantMajor}: the smallest bundled major >= wantMajor (else the newest),
     * or null while that runtime is not extracted yet.
     */
    File javaBinary(int wantMajor);

    /** Extracts the runtime {@link #javaBinary} would pick, if needed. Blocking (~1 min the first time). */
    void prepareJava(int wantMajor, LogBuffer log) throws IOException;

    /** Extra environment for child processes (LD_LIBRARY_PATH, HOME, TMPDIR...); java may be null (playitd). */
    Map<String, String> childEnv(File java);

    /** playitd executable, or null when this build has none. */
    File playitBinary();

    /** One-time setup at launch (extracts playitd). Blocking. */
    void prepareRuntime(LogBuffer log) throws IOException;

    /** Static panel file (path like "index.html"), or null if missing. */
    InputStream openAsset(String path) throws IOException;

    /** JSON object with battery info, or "null". */
    String batteryJson();

    /** Called when a user-visible summary changes (notification text). */
    void onSummary(String summary);

    /** One-off notification (server ready, crash, player joined, phone hot); desktop builds ignore it. */
    default void notify(String title, String text) {
    }

    /** "android", "windows", ...: tells the panel which device it runs on, so it can word things for it. */
    default String name() {
        return "android";
    }

    /** Resident memory of a child process in MB, or -1. Default: /proc/<pid>/status (Android, Linux). */
    default long processRssMb(int pid) {
        if (pid <= 0) return -1;
        try {
            for (String l : IO.readText(new File("/proc/" + pid + "/status")).split("\n")) {
                if (l.startsWith("VmRSS:")) return Long.parseLong(l.replaceAll("[^0-9]", "")) / 1024;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** CPU time a child process has used so far in ms, or -1. Default: utime + stime from /proc/<pid>/stat (10 ms ticks). */
    default long processCpuMs(int pid) {
        if (pid <= 0) return -1;
        try {
            String s = IO.readText(new File("/proc/" + pid + "/stat"));
            String[] f = s.substring(s.lastIndexOf(')') + 2).trim().split("\\s+"); // f[0] is field 3 (state)
            return (Long.parseLong(f[11]) + Long.parseLong(f[12])) * 10;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Total physical RAM in MB, or -1. Default: /proc/meminfo. */
    default long totalRamMb() {
        try {
            for (String l : IO.readText(new File("/proc/meminfo")).split("\n")) {
                if (l.startsWith("MemTotal:")) return Long.parseLong(l.replaceAll("[^0-9]", "")) / 1024;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
