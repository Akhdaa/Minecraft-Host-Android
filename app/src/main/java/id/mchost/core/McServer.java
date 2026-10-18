package id.mchost.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The Fabric dedicated server process. */
public class McServer extends ManagedProcess {
    public static final String LAUNCHER = "fabric-server-launch.jar";

    private static final Pattern DONE = Pattern.compile("\\]: Done \\(");
    private static final Pattern JOIN = Pattern.compile("\\]: ([A-Za-z0-9_.]{2,20}) joined the game");
    private static final Pattern LEAVE = Pattern.compile("\\]: ([A-Za-z0-9_.]{2,20}) left the game");
    private static final Pattern SAVED = Pattern.compile("Saved the game|Saved the world");

    private final Host host;
    private volatile String state = "stopped";
    private final TreeSet<String> players = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final LinkedList<Long> crashes = new LinkedList<>();
    private volatile long lastSaved;
    private volatile boolean restarting;

    public McServer(Host host, LogBuffer log) {
        super(log);
        this.host = host;
    }

    public String state() {
        return state;
    }

    public synchronized List<String> players() {
        return new ArrayList<>(players);
    }

    /** The active server has software installed: its launch target ("-jar x.jar" or "@args.txt") exists. */
    public boolean installed() {
        if (!host.hasServer()) return false;
        String[] launch = host.srv.get(Config.LAUNCH, "").trim().split(" ");
        String target = launch[launch.length - 1];
        if (target.startsWith("@")) target = target.substring(1);
        return !target.isEmpty() && new File(host.serverDir, target).exists();
    }

    public synchronized void start() throws IOException {
        if (alive()) throw new IOException("Server sudah jalan");
        if (!installed()) throw new IOException("Software server belum terpasang, buka pengaturan server lalu Install");
        File java = host.platform.javaBinary(host.javaMajor());
        if (java == null || !java.exists()) throw new IOException("Java " + host.javaMajor() + " belum siap");
        if (!host.cfg.getBool(Config.EULA)) throw new IOException("Setujui EULA Minecraft dulu");
        IO.writeText(new File(host.serverDir, "eula.txt"), "eula=true\n");
        File tmp = new File(host.base, "tmp");
        tmp.mkdirs();

        int ram = Math.max(256, host.srv.getInt(Config.RAM_MB, 3072));
        List<String> cmd = new ArrayList<>();
        cmd.add(java.getAbsolutePath());
        cmd.add("-Xms" + Math.min(1024, ram) + "M");
        cmd.add("-Xmx" + ram + "M");
        cmd.add("-XX:+UseG1GC");
        cmd.add("-XX:MaxGCPauseMillis=130");
        cmd.add("-XX:+ParallelRefProcEnabled");
        cmd.add("-XX:-UsePerfData");
        cmd.add("-Djava.io.tmpdir=" + tmp.getAbsolutePath());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dterminal.jline=false");
        cmd.add("-Dlog4j2.formatMsgNoLookups=true");
        String extra = host.srv.get(Config.JVM_ARGS, "").trim();
        if (!extra.isEmpty()) Collections.addAll(cmd, extra.split("\\s+"));
        String launch = host.srv.get(Config.LAUNCH, "").trim();
        Collections.addAll(cmd, launch.split(" "));
        cmd.add("nogui");

        Map<String, String> env = host.platform.childEnv(java);
        synchronized (this) {
            players.clear();
        }
        log.panel("Menyalakan " + host.srv.get(Config.NAME, "server") + " (Java " + host.javaMajor() + ", RAM " + ram + " MB)...");
        launch(cmd, host.serverDir, env, launch.substring(launch.lastIndexOf(' ') + 1));
        state = "starting";
        host.summaryChanged();
        startTunnelWithServer();
    }

    /** "Mulai bersamaan dengan server": bring the playit tunnel up together with the server. */
    private void startTunnelWithServer() {
        Playit p = host.playit;
        if (!host.cfg.getBool(Config.PLAYIT_WITH_SERVER) || p == null || p.alive() || !p.available()
                || host.cfg.get(Config.PLAYIT_SECRET, "").trim().isEmpty()) return;
        try {
            p.start();
            log.panel("Tunnel playit ikut dinyalakan bersama server.");
        } catch (IOException e) {
            log.panel("Tunnel gagal dinyalakan: " + e.getMessage());
        }
    }

    /** Graceful stop: "stop" command, force kill after 60s. */
    public void stop() {
        if (!alive()) return;
        stopRequested = true;
        state = "stopping";
        host.summaryChanged();
        log.panel("Mematikan server...");
        if (!send("stop")) kill();
        Thread t = new Thread(() -> {
            if (!waitGone(60_000)) {
                log.panel("Server tidak merespon, dipaksa mati.");
                kill();
            }
        }, "mc-stop-watch");
        t.setDaemon(true);
        t.start();
    }

    public void stopAndWait(long ms) {
        stop();
        if (!waitGone(ms)) kill();
        waitGone(5000);
    }

    public void restart() {
        Thread t = new Thread(() -> {
            restarting = true;
            try {
                stopAndWait(60_000);
                start();
            } catch (IOException e) {
                log.panel("Gagal restart: " + e.getMessage());
            } finally {
                restarting = false;
            }
        }, "mc-restart");
        t.setDaemon(true);
        t.start();
    }

    /** Sends save-all flush and waits until the server confirms (or timeout). */
    public boolean saveAndWait(long ms) {
        if (!"running".equals(state)) return false;
        long before = lastSaved;
        send("save-all flush");
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (lastSaved != before) return true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    protected void onLine(String line) {
        if (!"running".equals(state) && DONE.matcher(line).find()) {
            state = "running";
            log.panel("Server siap dimainkan.");
            host.summaryChanged();
            return;
        }
        if (SAVED.matcher(line).find()) lastSaved = System.nanoTime();
        Matcher m = JOIN.matcher(line);
        if (m.find()) {
            synchronized (this) {
                players.add(m.group(1));
            }
            host.summaryChanged();
            return;
        }
        m = LEAVE.matcher(line);
        if (m.find()) {
            synchronized (this) {
                players.remove(m.group(1));
            }
            host.summaryChanged();
        }
    }

    @Override
    protected void onExit(int code, boolean requested) {
        synchronized (this) {
            players.clear();
        }
        if (requested || code == 0) {
            state = "stopped";
            log.panel("Server mati (exit " + code + ").");
            host.summaryChanged();
            if (!restarting && host.cfg.getBool(Config.PLAYIT_WITH_SERVER) && host.playit.alive()) {
                log.panel("Tunnel playit ikut dimatikan.");
                host.playit.stop();
            }
            return;
        }
        state = "crashed";
        log.panel("Server crash (exit " + code + "). Cek log di atas.");
        host.summaryChanged();
        if (!host.cfg.getBool(Config.AUTO_RESTART)) return;
        long now = System.currentTimeMillis();
        synchronized (crashes) {
            crashes.add(now);
            while (!crashes.isEmpty() && now - crashes.getFirst() > 10 * 60_000) crashes.removeFirst();
            if (crashes.size() > 3) {
                log.panel("Crash 4x dalam 10 menit, auto restart dihentikan.");
                return;
            }
        }
        log.panel("Auto restart dalam 10 detik...");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(10_000);
                if (!alive() && "crashed".equals(state)) start();
            } catch (Exception e) {
                log.panel("Auto restart gagal: " + e.getMessage());
            }
        }, "mc-autorestart");
        t.setDaemon(true);
        t.start();
    }
}
