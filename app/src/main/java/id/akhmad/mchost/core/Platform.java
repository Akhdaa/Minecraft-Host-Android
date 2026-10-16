package id.akhmad.mchost.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Everything the core needs from the host OS (Android app, or a desktop JVM for testing). */
public interface Platform {
    /** App-private base directory (server/, backups/, runtime/ live under it). */
    File baseDir();

    /** Java executable for the Minecraft server, or null while the runtime is not ready. */
    File javaBinary();

    /** Extra environment for child processes (LD_LIBRARY_PATH, HOME, TMPDIR...). */
    Map<String, String> childEnv();

    /** playitd executable, or null when this build has none. */
    File playitBinary();

    /** Prepares the bundled runtime (extract on first launch). Blocking. */
    void prepareRuntime(LogBuffer log) throws IOException;

    /** Static panel file (path like "index.html"), or null if missing. */
    InputStream openAsset(String path) throws IOException;

    /** JSON object with battery info, or "null". */
    String batteryJson();

    /** Called when a user-visible summary changes (notification text). */
    void onSummary(String summary);
}
