package id.mchost.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** playit.gg tunnel agent (playitd) running headless with a secret key. */
public class Playit extends ManagedProcess {
    private final Host host;
    private volatile String state = "stopped";

    public Playit(Host host, LogBuffer log) {
        super(log);
        this.host = host;
    }

    public boolean available() {
        File b = host.platform.playitBinary();
        return b != null && b.exists();
    }

    public String state() {
        return state;
    }

    public synchronized void start() throws IOException {
        if (alive()) throw new IOException("Tunnel sudah jalan");
        if (!available()) throw new IOException("Build APK ini tidak membawa playitd");
        String secret = host.cfg.get(Config.PLAYIT_SECRET, "").trim();
        if (secret.isEmpty()) throw new IOException("Isi secret key playit dulu");
        File dir = new File(host.base, "playit");
        dir.mkdirs();
        List<String> cmd = new ArrayList<>();
        cmd.add(host.platform.playitBinary().getAbsolutePath());
        cmd.add("--secret");
        cmd.add(secret);
        cmd.add("--socket-path");
        cmd.add(File.separatorChar == '\\' ? "\\\\.\\pipe\\mchost-playitd" // Windows takes a named pipe here
                : new File(dir, "playitd.sock").getAbsolutePath());
        cmd.add("--platform-docker");
        log.panel("Menyalakan tunnel playit...");
        launch(cmd, dir, host.platform.childEnv(null), "playitd");
        state = "running";
    }

    public void stop() {
        if (!alive()) return;
        log.panel("Mematikan tunnel...");
        kill();
    }

    private static final String RUNDATA = System.getProperty("mchost.playitApi", "https://api.playit.gg") + "/v1/agents/rundata";
    private String cachedSecret;
    private String cachedJson;
    private long cachedAt;

    /**
     * Tunnel list from playit's API (same call playitd makes), as the raw JSON envelope
     * {"status":"success","data":{"tunnels":[{"display_address":...}],...}}. Cached for 20 s.
     */
    public synchronized String tunnelsJson(boolean force) throws IOException {
        String secret = host.cfg.get(Config.PLAYIT_SECRET, "").trim();
        if (secret.isEmpty()) return null;
        long now = System.currentTimeMillis();
        if (!force && cachedJson != null && secret.equals(cachedSecret) && now - cachedAt < 20_000) return cachedJson;
        byte[] body = IO.postJson(RUNDATA, "{}", "Agent-Key " + secret, 2 * 1024 * 1024);
        cachedJson = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        cachedSecret = secret;
        cachedAt = now;
        return cachedJson;
    }

    @Override
    protected void onLine(String line) {
    }

    @Override
    protected void onExit(int code, boolean requested) {
        state = requested ? "stopped" : "crashed";
        log.panel("Tunnel berhenti (exit " + code + ").");
    }
}
