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

/**
 * Owns the servers, tunnel, background tasks and file layout. Every server lives in servers/&lt;id&gt;/ with its
 * own settings in mchost.properties and backups in backups/&lt;id&gt;/. One server is "active": the panel shows it
 * and only it can run (a phone has RAM for one).
 */
public class Host {
    public static final String META = "mchost.properties";

    public final Platform platform;
    public final File base, serversRoot, backupsRoot;
    /** The active server's folder, backup folder and settings. Swapped by {@link #selectServer}. */
    public volatile File serverDir, backupDir;
    public volatile Config srv;
    private volatile String activeId = "";
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
        this.serversRoot = new File(base, "servers");
        this.backupsRoot = new File(base, "backups");
        this.cfg = new Config(new File(base, "panel.properties"));
        migrateSingleServer();
        serversRoot.mkdirs();
        this.mc = new McServer(this, mcLog);
        this.playit = new Playit(this, tunnelLog);
        List<String> ids = serverIds();
        String want = cfg.get(Config.ACTIVE, "");
        activate(ids.contains(want) ? want : ids.isEmpty() ? "" : ids.get(0));
        timer.scheduleWithFixedDelay(this::autoBackupTick, 2, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    /** Before multi-server, the one server lived in server/ and its backups in backups/: it becomes servers/s1. */
    private void migrateSingleServer() {
        File legacy = new File(base, "server");
        if (!legacy.isDirectory() || serversRoot.exists()) return;
        File to = new File(serversRoot, "s1");
        serversRoot.mkdirs();
        if (!legacy.renameTo(to)) {
            mcLog.panel("Gagal memindah folder server lama ke servers/s1");
            return;
        }
        File bdir = new File(backupsRoot, "s1");
        bdir.mkdirs();
        File[] zips = backupsRoot.listFiles((d, n) -> n.endsWith(".zip"));
        if (zips != null) for (File z : zips) //noinspection ResultOfMethodCallIgnored
            z.renameTo(new File(bdir, z.getName()));
        Config m = new Config(new File(to, META), false);
        String mcVer = cfg.get(Config.MC_VERSION, "1.20.1");
        m.set(Config.NAME, "Fabric " + mcVer);
        m.set(Config.TYPE, "fabric");
        for (String k : new String[]{Config.MC_VERSION, Config.LOADER, Config.INSTALLER, Config.RAM_MB, Config.JVM_ARGS}) {
            m.set(k, cfg.get(k, ""));
        }
        m.set(Config.LAUNCH, new File(to, McServer.LAUNCHER).exists() ? "-jar " + McServer.LAUNCHER : "");
        m.set(Config.JAVA, "17"); // what the single-server builds bundled
        m.set("created", String.valueOf(to.lastModified()));
        m.save();
        cfg.set(Config.ACTIVE, "s1");
        cfg.save();
        mcLog.panel("Server lama dipindah ke daftar server (s1).");
    }

    // ------------------------------------------------------------ servers

    /** Server ids (folder names) in creation order. */
    public List<String> serverIds() {
        File[] dirs = serversRoot.listFiles(f -> f.isDirectory() && new File(f, META).isFile());
        List<String> ids = new ArrayList<>();
        if (dirs != null) for (File d : dirs) ids.add(d.getName());
        ids.sort((a, b) -> Long.compare(idNum(a), idNum(b)));
        return ids;
    }

    private static long idNum(String id) {
        try {
            return Long.parseLong(id.substring(1));
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    public String activeId() {
        return activeId;
    }

    public boolean hasServer() {
        return !activeId.isEmpty();
    }

    /** Settings of any server; the active one shares {@link #srv} so edits are never lost to a stale copy. */
    public Config meta(String id) {
        if (id.equals(activeId)) return srv;
        if (!serverIds().contains(id)) throw new IllegalArgumentException("Server tidak ditemukan");
        return new Config(new File(new File(serversRoot, id), META), false);
    }

    public String type() {
        return srv.get(Config.TYPE, "fabric");
    }

    private synchronized void activate(String id) {
        activeId = id;
        String dir = id.isEmpty() ? ".none" : id; // ".none" is never created: endpoints check hasServer() first
        serverDir = new File(serversRoot, dir);
        backupDir = new File(backupsRoot, dir);
        srv = new Config(new File(serverDir, META), false);
        if (!id.isEmpty()) backupDir.mkdirs();
        cfg.set(Config.ACTIVE, id);
        cfg.save();
    }

    private void requireIdle() {
        if (mc.alive()) throw new IllegalStateException("Matikan server " + srv.get(Config.NAME, "") + " dulu");
        if (!task.isEmpty()) throw new IllegalStateException("Masih ada proses: " + task);
    }

    public synchronized void selectServer(String id) {
        if (id.equals(activeId)) return;
        if (!serverIds().contains(id)) throw new IllegalArgumentException("Server tidak ditemukan");
        requireIdle();
        activate(id);
        summaryChanged();
    }

    public static String cleanName(String name) {
        name = name == null ? "" : name.replaceAll("[\\r\\n\\t]", " ").trim();
        if (name.isEmpty() || name.length() > 40) throw new IllegalArgumentException("Nama server 1 sampai 40 huruf");
        return name;
    }

    /** Creates servers/sN, makes it active and installs the software in the background. */
    public synchronized String createServer(String name, String type, String mcVer, String loader, int ramMb) {
        name = cleanName(name);
        if (!Software.valid(type)) throw new IllegalArgumentException("Jenis server tidak dikenal");
        Software.checkVersion(mcVer, false);
        Software.checkVersion(loader, true);
        requireIdle();
        int n = 1;
        while (new File(serversRoot, "s" + n).exists()) n++;
        String id = "s" + n;
        File dir = new File(serversRoot, id);
        if (!dir.mkdirs()) throw new IllegalStateException("Gagal membuat folder server");
        Config m = new Config(new File(dir, META), false);
        m.set(Config.NAME, name);
        m.set(Config.TYPE, type);
        m.set(Config.MC_VERSION, mcVer);
        m.set(Config.RAM_MB, String.valueOf(ramMb));
        m.set(Config.JVM_ARGS, "");
        m.set("created", String.valueOf(System.currentTimeMillis()));
        m.save();
        try {
            IO.writeText(new File(dir, "server.properties"), defaultProperties(name));
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menulis server.properties: " + e.getMessage());
        }
        activate(id);
        install(mcVer, loader);
        return id;
    }

    /** Deletes a server and its backups. The active one moves to another server first. */
    public synchronized void deleteServer(String id) {
        if (!serverIds().contains(id)) throw new IllegalArgumentException("Server tidak ditemukan");
        if (id.equals(activeId)) {
            requireIdle();
            List<String> rest = serverIds();
            rest.remove(id);
            activate(rest.isEmpty() ? "" : rest.get(0));
        }
        File dir = new File(serversRoot, id), bdir = new File(backupsRoot, id);
        File trash = new File(serversRoot, ".del-" + id + "-" + System.nanoTime());
        if (!dir.renameTo(trash)) throw new IllegalStateException("Gagal menghapus server");
        runTask("Hapus server", () -> {
            IO.deleteRecursive(trash);
            IO.deleteRecursive(bdir);
            mcLog.panel("Server " + id + " dihapus.");
        });
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

    /** Prepares the runtime (and the active server's Java) in the background, then honours auto-start settings. */
    public void init() {
        runTask("Menyiapkan runtime (sekali saja, ~1 menit)", () -> {
            platform.prepareRuntime(mcLog);
            if (mc.installed()) platform.prepareJava(javaMajor(), mcLog);
            runtimeReady = true;
            mcLog.panel("Runtime siap.");
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

    /** Java major the active server needs (from Mojang's manifest at install time). */
    public int javaMajor() {
        return srv.getInt(Config.JAVA, 21);
    }

    /** Starts the active server, extracting its Java first when this is the first server on that Java. */
    public void startServer() throws IOException {
        if (!hasServer()) throw new IllegalStateException("Buat server dulu");
        if (platform.javaBinary(javaMajor()) != null) {
            mc.start();
            return;
        }
        int major = javaMajor();
        runTask("Menyiapkan Java " + major + " (sekali saja, ~1 menit)", () -> {
            platform.prepareJava(major, mcLog);
            mc.start();
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

    // ------------------------------------------------------------ install

    /** Installs (or switches the version of) the active server's software. {@code loader} empty = newest. */
    public void install(String mcVer, String loader) {
        Software.checkVersion(mcVer, false);
        Software.checkVersion(loader, true);
        if (!hasServer()) throw new IllegalStateException("Buat server dulu");
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu sebelum install ulang");
        String type = type();
        File dir = serverDir;
        Config m = srv;
        runTask("Install " + Software.label(type) + " " + mcVer, () -> {
            int java = Software.javaFor(mcVer);
            File bin = null;
            if ("forge".equals(type) || "neoforge".equals(type)) {
                platform.prepareJava(java, mcLog);
                bin = platform.javaBinary(java);
            }
            mcLog.panel("Download " + Software.label(type) + " " + mcVer + "...");
            Software.Installed res = Software.install(type, mcVer, loader, dir, bin, this);
            String old = m.get(Config.MC_VERSION, "");
            if ("fabric".equals(type) && !old.equals(mcVer)) {
                // the vanilla jar the Fabric launcher cached is for the old version
                File vanilla = new File(dir, "server.jar");
                if (vanilla.exists()) //noinspection ResultOfMethodCallIgnored
                    vanilla.delete();
            }
            m.set(Config.MC_VERSION, mcVer);
            m.set(Config.LOADER, res.loader);
            m.set(Config.LAUNCH, res.launch);
            m.set(Config.JAVA, String.valueOf(java));
            m.save();
            File props = new File(dir, "server.properties");
            if (!props.exists()) IO.writeText(props, defaultProperties(m.get(Config.NAME, "Server Minecraft dari HP")));
            mcLog.panel(Software.label(type) + " " + mcVer + " terpasang (Java " + java + "). Tekan Start.");
        });
    }

    private static String defaultProperties(String motd) {
        return "motd=" + motd.replace("\\", "") + "\n"
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

    /** Paper/Purpur keep Nether and End in world_nether/ and world_the_end/ instead of inside world/. */
    private File splitDim(String which) {
        return new File(serverDir, worldDir().getName() + ("nether".equals(which) ? "_nether" : "_the_end"));
    }

    /** Folder holding one dimension's data: "nether" or "end", for both world layouts. */
    public File dimDir(String which) {
        File split = splitDim(which);
        return split.isDirectory() ? split : new File(worldDir(), "nether".equals(which) ? "DIM-1" : "DIM1");
    }

    /** Every folder that belongs to the world: world/ plus world_nether/ and world_the_end/ when they exist. */
    public List<File> worldDirs() {
        List<File> out = new ArrayList<>();
        out.add(worldDir());
        for (String d : new String[]{"nether", "end"}) if (splitDim(d).isDirectory()) out.add(splitDim(d));
        return out;
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
            List<File> dirs = worldDirs();
            long total = 0;
            for (File d : dirs) total += IO.dirSize(d);
            try {
                IO.zipDirs(dirs, tmp, tracker(total));
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
            // world/ plus the Paper-style split dimensions; each old copy is kept aside until the restore succeeds
            File[] dirs = {world, splitDim("nether"), splitDim("end")};
            File[] old = new File[dirs.length];
            for (int i = 0; i < dirs.length; i++) {
                old[i] = new File(serverDir, dirs[i].getName() + ".before-restore");
                IO.deleteRecursive(old[i]);
                if (dirs[i].exists() && !dirs[i].renameTo(old[i])) {
                    for (int j = 0; j < i; j++) //noinspection ResultOfMethodCallIgnored
                        old[j].renameTo(dirs[j]);
                    throw new IOException("Gagal memindah " + dirs[i].getName() + " lama");
                }
            }
            try {
                IO.unzipWorld(zip, world, tracker(zip.length()));
                for (int i = 1; i < dirs.length; i++) IO.unzipPrefix(zip, dirs[i].getName() + "/", dirs[i]);
            } catch (Exception e) {
                for (int i = 0; i < dirs.length; i++) {
                    IO.deleteRecursive(dirs[i]);
                    //noinspection ResultOfMethodCallIgnored
                    old[i].renameTo(dirs[i]);
                }
                throw e;
            } finally {
                progress = -1;
            }
            for (File o : old) IO.deleteRecursive(o);
            mcLog.panel("World dipulihkan dari " + name);
        });
    }

    /** Deletes one dimension so it regenerates: "nether" (DIM-1) or "end" (DIM1). */
    public void resetDimension(String which) {
        final String label;
        if ("nether".equals(which)) label = "Nether";
        else if ("end".equals(which)) label = "End";
        else throw new IllegalArgumentException("Dimensi tidak dikenal");
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu");
        File d = dimDir(which);
        if (!d.exists()) throw new IllegalArgumentException(label + " belum pernah dibuat");
        runTask("Reset " + label, () -> {
            IO.deleteRecursive(d);
            mcLog.panel(label + " direset. Akan dibuat ulang saat ada pemain masuk ke sana.");
        });
    }

    public void resetWorld() {
        if (mc.alive()) throw new IllegalStateException("Matikan server dulu");
        runTask("Hapus world", () -> {
            for (File d : worldDirs()) IO.deleteRecursive(d);
            mcLog.panel("World dihapus. Start server untuk generate world baru.");
        });
    }

    // ------------------------------------------------------------ summary

    public String summary() {
        if (!task.isEmpty()) return task + "...";
        String s = mc.state();
        switch (s) {
            case "running":
                return srv.get(Config.NAME, "Server") + " jalan, " + mc.players().size() + " pemain online";
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
