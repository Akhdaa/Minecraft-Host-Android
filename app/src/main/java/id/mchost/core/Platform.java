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
}
