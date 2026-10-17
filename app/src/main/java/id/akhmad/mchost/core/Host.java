package id.akhmad.mchost.core;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns the Minecraft server, tunnel, background tasks and file layout. */
public class Host {
    public final Platform platform;
    public final File base, serverDir, backupDir;
    public final Config cfg;
    public final LogBuffer mcLog = new LogBuffer(4000);
    public final LogBuffer tunnelLog = new LogBuffer(800);
    public final McServer mc;
    public final Playit playit;

    private final ExecutorService tasks = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "host-task");
        t.setDaemon(true);
        return t;
    });
    private volatile String task = "";
    private volatile String taskError = "";
    private volatile boolean runtimeReady;
    private volatile int progress = -1;
    private final java.util.concurrent.ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "host-timer");
        t.setDaemon(true);
        return t;
    });

    public Host(Platform platform) {
        this.platform = platform;
        this.base = platform.baseDir();
        this.serverDir = new File(base, "server");
        this.backupDir = new File(base, "backups");
        serverDir.mkdirs();
        backupDir.mkdirs();
        new File(serverDir, "mods").mkdirs();
        this.cfg = new Config(new File(base, "panel.properties"));
        this.mc = new McServer(this, mcLog);
        this.playit = new Playit(this, tunnelLog);
        timer.scheduleWithFixedDelay(this::autoBackupTick, 2, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    /** Percentage (0..99) of the running backup/restore task, or -1 when unknown. */
    public int progress() {
        return progress;
    }

    private IO.Progress tracker(long total) {
        final long[] done = {0};
        progress = total > 0 ? 0 : -1;
        return n -> {
            done[0] += n;
            if (total > 0) progress = (int) Math.min(99, done[0] * 100 / total);
        };
    }

    /** Prepares the runtime in the background, then honours auto-start settings. */
    public void init() {
        runTask("Menyiapkan Java runtime (sekali saja, ~1 menit)", () -> {
            platform.prepareRuntime(mcLog);
            runtimeReady = true;
            mcLog.panel("Java runtime siap.");
            if (cfg.getBool(Config.PLAYIT_AUTOSTART) && playit.available()
                    && !cfg.get(Config.PLAYIT_SECRET, "").isEmpty()) {
                try {
                    playit.start();
                } catch (IOException e) {
                    tunnelLog.panel(e.getMessage());
                }
            }
            if (cfg.getBool(Config.AUTO_START) && mc.installed()) mc.start();
        });
    }

    public boolean runtimeReady() {
        return runtimeReady;
    }

    public String task() {
        return task;
    }

    public String taskError() {
        return taskError;
    }

    public interface Job {
        void run() throws Exception;
    }

    public synchronized void runTask(String name, Job job) {
        if (!task.isEmpty()) throw new IllegalStateException("Masih ada proses: " + task);
        task = name;
        taskError = "";
        summaryChanged();
        tasks.execute(() -> {
            try {
                job.run();
            } catch (Throwable e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                taskError = name + ": " + msg;
                mcLog.panel("GAGAL " + taskError);
            } finally {
                task = "";
                summaryChanged();
            }
        });
    }

    // ------------------------------------------------------------ fabric

    public void installFabric(String mcVer, String loader, String installer) {
        if (!mcVer.matches("[0-9A-Za-z.\\-_+ ]{1,40}") || !loader.matches("[0-9A-Za-z.\\-_+]{1,40}")
                || !installer.matches("[0-9A-Za-z.\\-_+]{1,40}")) {
            throw new IllegalArgumentException("Versi tidak valid");
        }
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu sebelum install ulang Fabric");
        runTask("Install Fabric " + mcVer + " (loader " + loader + ")", () -> {
            String url = "https://meta.fabricmc.net/v2/versions/loader/" + mcVer + "/" + loader + "/" + installer + "/server/jar";
            mcLog.panel("Download Fabric server launcher...");
            IO.download(url, new File(serverDir, McServer.LAUNCHER), mcLog);
            String old = cfg.get(Config.MC_VERSION, "");
            cfg.set(Config.MC_VERSION, mcVer);
            cfg.set(Config.LOADER, loader);
            cfg.set(Config.INSTALLER, installer);
            cfg.save();
            File props = new File(serverDir, "server.properties");
            if (!props.exists()) IO.writeText(props, defaultProperties());
            if (!old.equals(mcVer)) {
                // vanilla jar for the old version is useless now; launcher downloads the right one
                File vanilla = new File(serverDir, "server.jar");
                if (vanilla.exists()) //noinspection ResultOfMethodCallIgnored
                    vanilla.delete();
            }
            mcLog.panel("Fabric terpasang. Start pertama akan download server vanilla " + mcVer + " otomatis.");
        });
    }

    private static String defaultProperties() {
        return "motd=Server Minecraft dari HP\n"
                + "server-port=25565\n"
                + "max-players=8\n"
                + "view-distance=6\n"
                + "simulation-distance=5\n"
                + "online-mode=true\n"
                + "difficulty=normal\n"
                + "gamemode=survival\n"
                + "pvp=true\n"
                + "spawn-protection=0\n"
                + "sync-chunk-writes=false\n"
                + "network-compression-threshold=256\n"
                + "entity-broadcast-range-percentage=75\n"
                + "level-name=world\n";
    }

    // ------------------------------------------------------------ properties

    public String property(String key, String def) {
        try {
            for (String line : IO.readText(new File(serverDir, "server.properties")).split("\n")) {
                line = line.trim();
                if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
            }
        } catch (IOException ignored) {
        }
        return def;
    }

    public File worldDir() {
        String name = property("level-name", "world");
        if (!IO.safeName(name)) name = "world";
        return new File(serverDir, name);
    }

    // ------------------------------------------------------------ backups

    public List<File> backups() {
        File[] f = backupDir.listFiles((d, n) -> n.endsWith(".zip"));
        if (f == null) return new ArrayList<>();
        Arrays.sort(f, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return new ArrayList<>(Arrays.asList(f));
    }

    public static final String AUTO_PREFIX = "auto-";

    public void createBackup() {
        createBackup(false);
    }

    public void createBackup(boolean auto) {
        File world = worldDir();
        if (!world.exists()) throw new IllegalStateException("World belum ada (start server sekali dulu)");
        runTask(auto ? "Backup otomatis" : "Backup world", () -> {
            boolean running = "running".equals(mc.state());
            if (running) {
                mc.send("save-off");
                if (!mc.saveAndWait(20_000)) mcLog.panel("save-all tidak terkonfirmasi, lanjut backup.");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File out = new File(backupDir, (auto ? AUTO_PREFIX : "") + world.getName() + "-" + stamp + ".zip");
            File tmp = new File(backupDir, ".tmp-" + stamp);
            try {
                IO.zipDir(world, tmp, world.getName(), tracker(IO.dirSize(world)));
                if (!tmp.renameTo(out)) throw new IOException("Gagal menyimpan backup");
            } finally {
                progress = -1;
                if (running) mc.send("save-on");
                if (tmp.exists()) //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
            }
            mcLog.panel((auto ? "Backup otomatis" : "Backup") + " tersimpan: " + out.getName() + " (" + out.length() / 1024 / 1024 + " MB)");
            if (auto) pruneAutoBackups();
        });
    }

    /** Keeps only the newest N automatic backups; manual and uploaded ones are never touched. */
    private void pruneAutoBackups() {
        int keep = Math.max(1, cfg.getInt(Config.BACKUP_KEEP, 5));
        int seen = 0;
        for (File f : backups()) {
            if (!f.getName().startsWith(AUTO_PREFIX)) continue;
            if (++seen > keep && f.delete()) mcLog.panel("Backup otomatis lama dihapus: " + f.getName());
        }
    }

    public long lastAutoBackup() {
        for (File f : backups()) if (f.getName().startsWith(AUTO_PREFIX)) return f.lastModified();
        return 0;
    }

    private void autoBackupTick() {
        try {
            int hours = cfg.getInt(Config.AUTO_BACKUP_HOURS, 0);
            if (hours <= 0 || !"running".equals(mc.state()) || !task.isEmpty() || !worldDir().exists()) return;
            long last = lastAutoBackup();
            if (last > 0 && System.currentTimeMillis() - last < hours * 3_600_000L) return;
            if (last == 0 && mc.uptimeSec() < 10 * 60) return; // first one after 10 min of play
            createBackup(true);
        } catch (Throwable ignored) {
        }
    }

    public void restoreBackup(String name) {
        File zip = new File(backupDir, name);
        if (!IO.safeName(name) || !zip.exists()) throw new IllegalArgumentException("Backup tidak ditemukan");
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu sebelum restore");
        runTask("Restore " + name, () -> {
            File world = worldDir();
            File old = new File(serverDir, world.getName() + ".before-restore");
            IO.deleteRecursive(old);
            if (world.exists() && !world.renameTo(old)) throw new IOException("Gagal memindah world lama");
            try {
                IO.unzipWorld(zip, world, tracker(zip.length()));
            } catch (Exception e) {
                IO.deleteRecursive(world);
                //noinspection ResultOfMethodCallIgnored
                old.renameTo(world);
                throw e;
            } finally {
                progress = -1;
            }
            IO.deleteRecursive(old);
            mcLog.panel("World dipulihkan dari " + name);
        });
    }

    /** Deletes one dimension so it regenerates: "nether" (DIM-1) or "end" (DIM1). */
    public void resetDimension(String which) {
        final String dir;
        final String label;
        if ("nether".equals(which)) {
            dir = "DIM-1";
            label = "Nether";
        } else if ("end".equals(which)) {
            dir = "DIM1";
            label = "End";
        } else throw new IllegalArgumentException("Dimensi tidak dikenal");
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu");
        File d = new File(worldDir(), dir);
        if (!d.exists()) throw new IllegalArgumentException(label + " belum pernah dibuat");
        runTask("Reset " + label, () -> {
            IO.deleteRecursive(d);
            mcLog.panel(label + " direset. Akan dibuat ulang saat ada pemain masuk ke sana.");
        });
    }

    public void resetWorld() {
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu");
        runTask("Hapus world", () -> {
            IO.deleteRecursive(worldDir());
            mcLog.panel("World dihapus. Start server untuk generate world baru.");
        });
    }

    // ------------------------------------------------------------ summary

    public String summary() {
        if (!task.isEmpty()) return task + "...";
        String s = mc.state();
        switch (s) {
            case "running":
                return "Server jalan, " + mc.players().size() + " pemain online";
            case "starting":
                return "Server sedang menyala...";
            case "stopping":
                return "Server sedang mati...";
            case "crashed":
                return "Server crash";
            default:
                return "Server mati, panel aktif";
        }
    }

    public void summaryChanged() {
        try {
            platform.onSummary(summary());
        } catch (Throwable ignored) {
        }
    }

    public void shutdown(long waitMs) {
        playit.stop();
        if (mc.alive()) mc.stopAndWait(waitMs);
        tasks.shutdownNow();
        timer.shutdownNow();
    }
}
