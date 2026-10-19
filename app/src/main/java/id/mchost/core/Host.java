package id.mchost.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
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
        lang = cfg.get(Config.LANG, "en");
        migrateSingleServer();
        serversRoot.mkdirs();
        this.mc = new McServer(this, mcLog);
        this.playit = new Playit(this, tunnelLog);
        List<String> ids = serverIds();
        String want = cfg.get(Config.ACTIVE, "");
        activate(ids.contains(want) ? want : ids.isEmpty() ? "" : ids.get(0));
        timer.scheduleWithFixedDelay(this::autoBackupTick, 2, 1, java.util.concurrent.TimeUnit.MINUTES);
        timer.scheduleWithFixedDelay(this::watchTick, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(this::perfTick, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
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
        endSleep(); // the listener belongs to the server that was active
        activeId = id;
        String dir = id.isEmpty() ? ".none" : id; // ".none" is never created: endpoints check hasServer() first
        serverDir = new File(serversRoot, dir);
        backupDir = new File(backupsRoot, dir);
        srv = new Config(new File(serverDir, META), false);
        if (!id.isEmpty()) backupDir.mkdirs();
        cfg.set(Config.ACTIVE, id);
        cfg.save();
    }

    void requireIdle() {
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
        String id = newServer(name, type, mcVer, ramMb);
        install(mcVer, loader);
        return id;
    }

    /** servers/sN with its mchost.properties and a default server.properties, made active; no software yet. */
    private synchronized String newServer(String name, String type, String mcVer, int ramMb) {
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
        endSleep();
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

    // ------------------------------------------------------------ sleep when empty, daily restart, notifications

    /** Per-server settings in mchost.properties. */
    public static final String SLEEP_MIN = "sleep_min", DAILY_RESTART = "daily_restart", BEDROCK_FILES = "bedrock_files";
    private volatile SleepListener sleeper;
    private volatile long emptySince, lastHeat;
    private volatile String lastRestartMark = "";

    public boolean sleeping() {
        return sleeper != null;
    }

    /** Called by McServer once a sleep-stop finished: hold the port so a join attempt wakes the server. */
    void startSleeping() {
        int port = intProperty("server-port", 25565), max = intProperty("max-players", 20);
        try {
            sleeper = new SleepListener(port, tr("Sleeping, join to wake it up", "Tidur, join untuk membangunkan"),
                    tr("The server is waking up, join again in about a minute", "Server sedang dibangunkan, join lagi sekitar 1 menit"),
                    max, this::wakeFromSleep);
        } catch (IOException e) {
            mcLog.panel(tr("Couldn't hold the port while sleeping: ", "Gagal menjaga port saat tidur: ") + e.getMessage());
            return;
        }
        mcLog.panel(tr("Server is asleep. It wakes up when someone joins.", "Server tidur. Akan bangun saat ada yang join."));
        notifyEvent(Config.NOTIFY_SERVER, srv.get(Config.NAME, "Server") + tr(" is asleep", " tidur"),
                tr("Nobody was online. It wakes up when someone joins.", "Tidak ada pemain. Server bangun saat ada yang join."));
        summaryChanged();
    }

    public void endSleep() {
        SleepListener s = sleeper;
        sleeper = null;
        if (s != null) {
            s.close();
            summaryChanged();
        }
    }

    private void wakeFromSleep() {
        sleeper = null;
        mcLog.panel(tr("Someone is joining, waking the server up.", "Ada yang join, server dibangunkan."));
        notifyEvent(Config.NOTIFY_SERVER, srv.get(Config.NAME, "Server") + tr(" is waking up", " dibangunkan"),
                tr("Someone tried to join.", "Ada yang mencoba join."));
        try {
            startServer();
        } catch (Exception e) {
            mcLog.panel(tr("Wake-up failed: ", "Gagal membangunkan server: ") + e.getMessage());
            summaryChanged();
        }
    }

    private int intProperty(String key, int def) {
        try {
            return Integer.parseInt(property(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Android notification, only when the user turned that kind on in Settings. */
    public void notifyEvent(String kind, String title, String text) {
        if (cfg.getBool(kind)) platform.notify(title, text);
    }

    private void watchTick() {
        try {
            sleepTick();
            dailyRestartTick();
            heatTick();
        } catch (Throwable ignored) {
        }
    }

    private void sleepTick() {
        int min = srv.getInt(SLEEP_MIN, 0);
        if (min <= 0 || !"running".equals(mc.state()) || !task.isEmpty() || !mc.players().isEmpty() || pregenRunning()) {
            emptySince = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (emptySince == 0) {
            emptySince = now;
            return;
        }
        if (now - emptySince < min * 60_000L) return;
        emptySince = 0;
        mcLog.panel(tr("Nobody online for " + min + " minutes, putting the server to sleep.",
                "Tidak ada pemain selama " + min + " menit, server ditidurkan."));
        mc.stopForSleep();
    }

    private void dailyRestartTick() {
        String at = srv.get(DAILY_RESTART, "").trim();
        if (!at.matches("\\d{1,2}:\\d{2}") || !"running".equals(mc.state()) || !task.isEmpty()) return;
        String[] p = at.split(":");
        int target = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        java.util.Calendar c = java.util.Calendar.getInstance();
        int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        int left = (target - now + 1440) % 1440;
        String mark = c.get(java.util.Calendar.DAY_OF_YEAR) + ":" + left;
        if (mark.equals(lastRestartMark) || (left != 5 && left != 1 && left != 0)) return;
        lastRestartMark = mark;
        if (left > 0) {
            mc.send("say " + tr("Daily restart in " + left + (left == 1 ? " minute" : " minutes"), "Restart harian dalam " + left + " menit"));
            return;
        }
        mcLog.panel(tr("Daily restart.", "Restart harian."));
        mc.restart();
    }

    private void heatTick() {
        if (!cfg.getBool(Config.NOTIFY_HEAT)) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"temp\"\\s*:\\s*([0-9.]+)").matcher(platform.batteryJson());
        if (!m.find()) return;
        double t = Double.parseDouble(m.group(1));
        long now = System.currentTimeMillis();
        if (t < 45 || now - lastHeat < 15 * 60_000L) return;
        lastHeat = now;
        notifyEvent(Config.NOTIFY_HEAT, tr("Phone is hot", "HP kepanasan"), String.format(Locale.US,
                tr("Battery at %.1f°C. Lower the view distance or let it cool down.", "Baterai %.1f°C. Turunkan view distance atau biarkan HP dingin dulu."), t));
    }

    // ------------------------------------------------------------ performance graph

    private static final int SAMPLES = 360; // one hour at one sample per 10 s
    /** Ring buffer rows: time ms, RSS MB, CPU % of all cores, battery °C x10, ms behind ("Can't keep up") in the interval. */
    private final long[][] perf = new long[SAMPLES][5];
    private int perfCount, perfHead;
    private long lastCpuTicks = -1, lastCpuAt;
    private final java.util.concurrent.atomic.AtomicLong lagMs = new java.util.concurrent.atomic.AtomicLong();

    void recordLag(long ms) {
        lagMs.addAndGet(ms);
    }

    private void perfTick() {
        try {
            if (!mc.alive()) {
                lastCpuTicks = -1;
                return;
            }
            long now = System.currentTimeMillis(), ticks = mc.cpuTicks(), cpu = -1;
            if (ticks >= 0 && lastCpuTicks >= 0 && now > lastCpuAt) {
                // ticks are 1/100 s: percent of one core = dTicks * 1000 / dMs
                cpu = Math.min(100, (ticks - lastCpuTicks) * 1000 / (now - lastCpuAt) / Runtime.getRuntime().availableProcessors());
            }
            lastCpuTicks = ticks;
            lastCpuAt = now;
            long temp = -1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"temp\"\\s*:\\s*([0-9.]+)").matcher(platform.batteryJson());
            if (m.find()) temp = Math.round(Double.parseDouble(m.group(1)) * 10);
            synchronized (perf) {
                long[] r = perf[perfHead];
                r[0] = now;
                r[1] = mc.rssMb();
                r[2] = cpu;
                r[3] = temp;
                r[4] = lagMs.getAndSet(0);
                perfHead = (perfHead + 1) % SAMPLES;
                if (perfCount < SAMPLES) perfCount++;
            }
        } catch (Throwable ignored) {
        }
    }

    /** The last hour of samples, oldest first, as parallel arrays. */
    public String perfJson() {
        StringBuilder[] a = {new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder(), new StringBuilder()};
        synchronized (perf) {
            for (int i = 0; i < perfCount; i++) {
                long[] r = perf[(perfHead - perfCount + i + SAMPLES) % SAMPLES];
                for (int k = 0; k < 5; k++) a[k].append(i > 0 ? "," : "").append(r[k]);
            }
        }
        return Json.obj().raw("t", "[" + a[0] + "]").raw("ram", "[" + a[1] + "]").raw("cpu", "[" + a[2] + "]")
                .raw("temp", "[" + a[3] + "]").raw("lag", "[" + a[4] + "]")
                .put("ramMax", srv.getInt(Config.RAM_MB, 3072)).put("cores", Runtime.getRuntime().availableProcessors()).toString();
    }

    // ------------------------------------------------------------ pre-generation (Chunky)

    private volatile String pregenState = "", pregenWorld = "", pregenEta = "", pregenRate = "";
    private volatile double pregenPct;

    /** Called by McServer for Chunky's progress lines. */
    void chunkyLine(String state, String world, String pct, String eta, String rate) {
        pregenState = "stopped".equals(state) ? "paused" : state; // "chunky pause" and a server stop both log "Task stopped"
        pregenWorld = world;
        if (pct != null) pregenPct = Double.parseDouble(pct);
        pregenEta = eta == null ? "" : eta;
        pregenRate = rate == null ? "" : rate;
    }

    public boolean pregenRunning() {
        return "running".equals(pregenState) && mc.alive();
    }

    private static List<String> chunkyLoaders(String type) {
        switch (type) {
            case "paper":
            case "purpur": return Arrays.asList("paper", "spigot", "bukkit");
            case "fabric": return Collections.singletonList("fabric");
            case "forge": return Collections.singletonList("forge");
            case "neoforge": return Collections.singletonList("neoforge");
            default: return null;
        }
    }

    private boolean chunkyInstalled() {
        String d = Software.addonDir(type());
        if (d == null) return false;
        String[] f = new File(serverDir, d).list((x, n) -> n.toLowerCase(Locale.US).startsWith("chunky") && n.endsWith(".jar"));
        return f != null && f.length > 0;
    }

    public String pregenJson() {
        String state = pregenState;
        if ("running".equals(state) && !mc.alive()) state = "paused"; // Chunky keeps its progress; "continue" resumes it
        return Json.obj().put("supported", hasServer() && chunkyLoaders(type()) != null).put("installed", hasServer() && chunkyInstalled())
                .put("running", "running".equals(mc.state())).put("state", state).put("world", pregenWorld)
                .put("pct", pregenPct).put("eta", pregenEta).put("rate", pregenRate).toString();
    }

    /** install | start (with radius) | pause | continue | cancel. */
    public void pregen(String action, int radius) {
        if (!hasServer()) throw new IllegalStateException("Buat server dulu");
        if ("install".equals(action)) {
            List<String> loaders = chunkyLoaders(type());
            if (loaders == null) throw new IllegalArgumentException(tr("Chunky doesn't run on ", "Chunky tidak bisa dipakai di ") + Software.label(type()));
            String mcVer = srv.get(Config.MC_VERSION, "");
            File dir = new File(serverDir, Software.addonDir(type()));
            runTask("Install Chunky", () -> {
                dir.mkdirs();
                Map<String, Object> v = modrinthVersion("chunky", loaders, mcVer);
                if (v == null) throw new IOException(tr("Chunky isn't available for ", "Chunky belum ada untuk ") + Software.label(type()) + " " + mcVer);
                downloadPrimary(v, dir);
                mcLog.panel(tr("Chunky installed. Restart the server, then start pre-generating.", "Chunky terpasang. Restart server, lalu mulai pre-generate."));
            });
            return;
        }
        if (!"running".equals(mc.state())) throw new IllegalStateException(tr("Start the server first", "Nyalakan server dulu"));
        if (!chunkyInstalled()) throw new IllegalStateException(tr("Install Chunky first", "Pasang Chunky dulu"));
        switch (action) {
            case "start":
                if (radius < 100 || radius > 20000) throw new IllegalArgumentException("radius 100..20000");
                mc.send("chunky spawn");
                mc.send("chunky radius " + radius);
                mc.send("chunky start");
                mc.send("chunky confirm"); // only needed when an old task for this world is still saved
                pregenState = "running";
                pregenPct = 0;
                pregenEta = "";
                break;
            case "pause":
                mc.send("chunky pause");
                pregenState = "paused";
                break;
            case "continue":
                mc.send("chunky continue");
                pregenState = "running";
                break;
            case "cancel":
                mc.send("chunky cancel");
                mc.send("chunky confirm");
                pregenState = "cancelled";
                break;
            default:
                throw new IllegalArgumentException("action?");
        }
    }

    // ------------------------------------------------------------ Modrinth: Bedrock (Geyser) and modpacks

    private static final String MR = "https://api.modrinth.com/v2/";

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }

    /** Newest release of a Modrinth project for these loaders and Minecraft version, or null. */
    static Map<String, Object> modrinthVersion(String project, List<String> loaders, String mcVer) throws IOException {
        String url = MR + "project/" + project + "/version?loaders=" + enc(Json.strArray(loaders))
                + "&game_versions=" + enc(Json.strArray(Collections.singletonList(mcVer)));
        List<Object> vs = Json.asList(Json.parse(new String(IO.fetch(url, 8 << 20), StandardCharsets.UTF_8)));
        for (Object v : vs) if ("release".equals(Json.asMap(v).get("version_type"))) return Json.asMap(v);
        return vs.isEmpty() ? null : Json.asMap(vs.get(0));
    }

    /** Downloads the primary file of a Modrinth version into dir and returns its file name. */
    static String downloadPrimary(Map<String, Object> version, File dir) throws IOException {
        List<Object> files = Json.asList(version.get("files"));
        Map<String, Object> f = null;
        for (Object o : files) if (Boolean.TRUE.equals(Json.asMap(o).get("primary"))) f = Json.asMap(o);
        if (f == null && !files.isEmpty()) f = Json.asMap(files.get(0));
        if (f == null) throw new IOException("Versi Modrinth tanpa file");
        String name = Json.asStr(f.get("filename")), url = Json.asStr(f.get("url"));
        if (!IO.safeName(name) || !url.startsWith("https://cdn.modrinth.com/")) throw new IOException("File Modrinth tidak valid: " + name);
        IO.download(url, new File(dir, name), null);
        return name;
    }

    private static List<String> bedrockLoaders(String type) {
        switch (type) {
            case "paper": return Arrays.asList("paper", "spigot");
            case "purpur": return Arrays.asList("purpur", "paper", "spigot");
            case "fabric": return Collections.singletonList("fabric");
            case "neoforge": return Collections.singletonList("neoforge");
            default: return null;
        }
    }

    /** Geyser + Floodgate (Bedrock players from phones and consoles), matched to this server's version; or removes them. */
    public void setBedrock(boolean on) {
        if (!hasServer()) throw new IllegalStateException("Buat server dulu");
        String type = type(), mcVer = srv.get(Config.MC_VERSION, "");
        List<String> loaders = bedrockLoaders(type);
        if (on && loaders == null) throw new IllegalArgumentException(tr("Bedrock isn't supported for ", "Bedrock belum didukung untuk ") + Software.label(type));
        String addons = Software.addonDir(type);
        if (addons == null) throw new IllegalArgumentException(tr("Bedrock isn't supported for ", "Bedrock belum didukung untuk ") + Software.label(type));
        File dir = new File(serverDir, addons);
        Config m = srv;
        runTask(on ? "Bedrock (Geyser)" : tr("Removing Bedrock", "Hapus Bedrock"), () -> {
            for (String f : m.get(BEDROCK_FILES, "").split(",")) {
                if (IO.safeName(f.trim())) //noinspection ResultOfMethodCallIgnored
                    new File(dir, f.trim()).delete();
            }
            m.set(BEDROCK_FILES, "");
            m.save();
            if (!on) {
                mcLog.panel(tr("Bedrock support removed. Restart the server.", "Dukungan Bedrock dihapus. Restart server."));
                return;
            }
            dir.mkdirs();
            Map<String, Object> geyser = modrinthVersion("geyser", loaders, mcVer);
            if (geyser == null) throw new IOException(tr("Geyser isn't available for ", "Geyser belum ada untuk ") + Software.label(type) + " " + mcVer);
            List<String> names = new ArrayList<>();
            names.add(downloadPrimary(geyser, dir));
            if ("paper".equals(type) || "purpur".equals(type)) {
                // Floodgate for Bukkit-family servers is only published by GeyserMC itself
                IO.download("https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot",
                        new File(dir, "floodgate-spigot.jar"), null);
                names.add("floodgate-spigot.jar");
            } else {
                Map<String, Object> flood = modrinthVersion("floodgate", loaders, mcVer);
                if (flood != null) names.add(downloadPrimary(flood, dir));
                else mcLog.panel(tr("Floodgate isn't available for this version: Bedrock players need a Java account.",
                        "Floodgate belum ada untuk versi ini: pemain Bedrock perlu akun Java."));
            }
            if ("fabric".equals(type)) {
                String[] have = dir.list((d, n) -> n.toLowerCase(Locale.US).startsWith("fabric-api"));
                if (have == null || have.length == 0) {
                    Map<String, Object> api = modrinthVersion("fabric-api", loaders, mcVer);
                    if (api != null) downloadPrimary(api, dir);
                }
            }
            m.set(BEDROCK_FILES, String.join(",", names));
            m.save();
            mcLog.panel(tr("Bedrock support installed (Geyser). Restart the server; Bedrock players join on port 19132.",
                    "Dukungan Bedrock terpasang (Geyser). Restart server; pemain Bedrock join di port 19132."));
        });
    }

    /** A new server from a Modrinth modpack (.mrpack): its loader, the server-side files and the overrides. */
    public void createFromModpack(String url, String name, int ramMb) {
        if (!url.startsWith("https://cdn.modrinth.com/")) throw new IllegalArgumentException("Hanya modpack dari cdn.modrinth.com");
        String clean = cleanName(name);
        requireIdle();
        runTask("Install modpack", () -> {
            File pack = new File(base, "import/pack-" + System.nanoTime() + ".mrpack");
            try {
                mcLog.panel(tr("Downloading modpack...", "Download modpack..."));
                IO.download(url, pack, mcLog);
                Map<String, Object> idx;
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(pack)) {
                    java.util.zip.ZipEntry e = z.getEntry("modrinth.index.json");
                    if (e == null) throw new IOException(tr("Not a Modrinth modpack (no modrinth.index.json)", "Bukan modpack Modrinth (modrinth.index.json tidak ada)"));
                    try (InputStream in = z.getInputStream(e)) {
                        idx = Json.asMap(Json.parse(new String(IO.readAll(in, 16 << 20), StandardCharsets.UTF_8)));
                    }
                }
                Map<String, Object> deps = Json.asMap(idx.get("dependencies"));
                String mcVer = Json.asStr(deps.get("minecraft")), type, loader;
                if (deps.containsKey("fabric-loader")) {
                    type = "fabric";
                    loader = Json.asStr(deps.get("fabric-loader"));
                } else if (deps.containsKey("neoforge")) {
                    type = "neoforge";
                    loader = Json.asStr(deps.get("neoforge"));
                } else if (deps.containsKey("forge")) {
                    type = "forge";
                    loader = Json.asStr(deps.get("forge"));
                } else {
                    throw new IOException(tr("This modpack uses a loader MC Host doesn't support (Quilt?)", "Modpack ini memakai loader yang belum didukung (Quilt?)"));
                }
                Software.checkVersion(mcVer, false);
                newServer(clean, type, mcVer, ramMb);
                File dir = serverDir;
                Config m = srv;
                String canon = dir.getCanonicalPath() + File.separator;
                List<Object> files = Json.asList(idx.get("files"));
                int done = 0;
                for (Object o : files) {
                    progress = done++ * 90 / Math.max(1, files.size());
                    Map<String, Object> f = Json.asMap(o);
                    if ("unsupported".equals(Json.asStr(Json.asMap(f.get("env")).get("server")))) continue;
                    String path = Json.asStr(f.get("path")).replace('\\', '/');
                    File dest = new File(dir, path);
                    if (path.isEmpty() || path.startsWith("/") || path.contains("..") || !dest.getCanonicalPath().startsWith(canon)) {
                        throw new IOException("Path modpack berbahaya: " + path);
                    }
                    String src = "";
                    for (Object d : Json.asList(f.get("downloads"))) {
                        if (mrpackHostOk(Json.asStr(d))) {
                            src = Json.asStr(d);
                            break;
                        }
                    }
                    if (src.isEmpty()) throw new IOException(tr("Download host not allowed: ", "Sumber unduhan tidak diizinkan: ") + path);
                    IO.download(src, dest, null);
                    String want = Json.asStr(Json.asMap(f.get("hashes")).get("sha1"));
                    if (!want.isEmpty() && !want.equalsIgnoreCase(IO.sha1(dest))) {
                        //noinspection ResultOfMethodCallIgnored
                        dest.delete();
                        throw new IOException(tr("Hash mismatch: ", "Hash tidak cocok: ") + path);
                    }
                }
                IO.unzipPrefix(pack, "overrides/", dir);
                IO.unzipPrefix(pack, "server-overrides/", dir);
                progress = -1;
                mcLog.panel(tr("Modpack files ready (" + files.size() + "). Installing ", "File modpack siap (" + files.size() + "). Install ")
                        + Software.label(type) + " " + mcVer + "...");
                doInstall(type, mcVer, loader, dir, m);
            } finally {
                progress = -1;
                //noinspection ResultOfMethodCallIgnored
                pack.delete();
            }
        });
    }

    /** Download hosts the .mrpack format allows. */
    private static boolean mrpackHostOk(String url) {
        try {
            String h = new java.net.URL(url).getHost();
            return url.startsWith("https://") && (h.equals("cdn.modrinth.com") || h.equals("github.com")
                    || h.equals("raw.githubusercontent.com") || h.equals("gitlab.com"));
        } catch (Exception e) {
            return false;
        }
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
        runTask("Install " + Software.label(type) + " " + mcVer, () -> doInstall(type, mcVer, loader, dir, m));
    }

    /** Downloads/installs the software into dir and records launch/loader/java in m (runs inside a task). */
    private void doInstall(String type, String mcVer, String loader, File dir, Config m) throws Exception {
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

    public static final String FULL_PREFIX = "full-";
    /** Left out of full backups: regenerated by the server, or not worth carrying around. */
    private static final Set<String> FULL_SKIP = new HashSet<>(Arrays.asList("logs", "crash-reports", ".cache", "session.lock"));

    public void createBackup() {
        createBackup(false);
    }

    /** World backup, or the whole server folder (mods, plugins, config) when "backup_full" is on. */
    public void createBackup(boolean auto) {
        File world = worldDir();
        boolean full = cfg.getBool(Config.BACKUP_FULL);
        if (!full && !world.exists()) throw new IllegalStateException("World belum ada (start server sekali dulu)");
        runTask(auto ? "Backup otomatis" : full ? "Backup lengkap" : "Backup world", () -> {
            boolean running = "running".equals(mc.state());
            if (running) {
                mc.send("save-off");
                if (!mc.saveAndWait(20_000)) mcLog.panel("save-all tidak terkonfirmasi, lanjut backup.");
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File out = new File(backupDir, (auto ? AUTO_PREFIX : "") + (full ? FULL_PREFIX : "") + world.getName() + "-" + stamp + ".zip");
            File tmp = new File(backupDir, ".tmp-" + stamp);
            List<File> dirs = full ? serverParts() : worldDirs();
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
        boolean full;
        try {
            full = IO.zipHas(zip, META);
        } catch (IOException e) {
            throw new IllegalArgumentException("Backup rusak: " + e.getMessage());
        }
        if (full) {
            restoreFull(zip, name);
            return;
        }
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

    /** Everything in the active server folder that a full backup carries. */
    private List<File> serverParts() {
        File[] kids = serverDir.listFiles(f -> !FULL_SKIP.contains(f.getName()));
        return kids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(kids));
    }

    /** Puts a full backup back: the whole server folder is swapped, the old one kept until it worked. */
    private void restoreFull(File zip, String name) {
        runTask("Restore " + name, () -> {
            File work = new File(base, "import/r-" + System.nanoTime());
            File old = new File(base, "import/old-" + System.nanoTime());
            try {
                IO.unzipAll(zip, work, tracker(zip.length()));
                File root = serverRoot(work);
                if (root == null) throw new IOException("Backup ini tidak berisi folder server");
                old.getParentFile().mkdirs();
                if (!serverDir.renameTo(old)) throw new IOException("Gagal memindah folder server lama");
                if (!root.renameTo(serverDir)) {
                    //noinspection ResultOfMethodCallIgnored
                    old.renameTo(serverDir);
                    throw new IOException("Gagal memasang folder server dari backup");
                }
                activate(activeId); // re-read mchost.properties from the restored folder
            } finally {
                progress = -1;
                IO.deleteRecursive(work);
                IO.deleteRecursive(old);
            }
            summaryChanged();
            mcLog.panel("Server dipulihkan lengkap dari " + name);
        });
    }

    /** Adds a server from a full backup or a zipped server folder as a new servers/sN and selects it. */
    public void importServer(File zip, boolean deleteZip) {
        requireIdle();
        runTask("Import server", () -> {
            File work = new File(base, "import/x-" + System.nanoTime());
            try {
                IO.unzipAll(zip, work, tracker(zip.length()));
                File root = serverRoot(work);
                if (root == null) throw new IOException("Zip ini bukan folder server Minecraft (server.properties tidak ada)");
                Config m = new Config(new File(root, META), false);
                if (m.get(Config.TYPE, "").isEmpty()) guessMeta(root, m, zip.getName());
                String name = m.get(Config.NAME, "Server");
                for (String other : serverIds()) {
                    if (meta(other).get(Config.NAME, "").equalsIgnoreCase(name)) {
                        name = (name.length() > 31 ? name.substring(0, 31) : name) + " (import)";
                        break;
                    }
                }
                m.set(Config.NAME, name);
                m.save();
                synchronized (this) {
                    int n = 1;
                    while (new File(serversRoot, "s" + n).exists()) n++;
                    if (!root.renameTo(new File(serversRoot, "s" + n))) throw new IOException("Gagal memindah server hasil import");
                    activate("s" + n);
                }
                summaryChanged();
                mcLog.panel("Server diimport: " + name + (mc.installed() ? "." : ". Buka pengaturan server lalu Install."));
            } finally {
                progress = -1;
                IO.deleteRecursive(work);
                if (deleteZip) //noinspection ResultOfMethodCallIgnored
                    zip.delete();
            }
        });
    }

    /** The extracted folder itself, or its single sub-folder (zips of a folder wrap it), holding the server. */
    private static File serverRoot(File dir) {
        if (isServerDir(dir)) return dir;
        File[] kids = dir.listFiles(File::isDirectory);
        return kids != null && kids.length == 1 && isServerDir(kids[0]) ? kids[0] : null;
    }

    private static boolean isServerDir(File d) {
        return new File(d, META).isFile() || new File(d, "server.properties").isFile();
    }

    /** A plain server folder without mchost.properties: work out the software from what is installed. */
    private static void guessMeta(File root, Config m, String zipName) throws IOException {
        String type, launch, jar;
        // jar names first: Paper's own libraries/ also holds net/neoforged remapping tools
        if (new File(root, McServer.LAUNCHER).isFile()) {
            type = "fabric";
            launch = "-jar " + McServer.LAUNCHER;
        } else if ((jar = jarStarting(root, "purpur")) != null) {
            type = "purpur";
            launch = "-jar " + jar;
        } else if ((jar = jarStarting(root, "paper")) != null) {
            type = "paper";
            launch = "-jar " + jar;
        } else if (new File(root, "libraries/net/neoforged/neoforge").isDirectory()
                || new File(root, "libraries/net/neoforged/forge").isDirectory()) {
            type = "neoforge";
            launch = Software.forgeLaunch(root);
        } else if (new File(root, "libraries/net/minecraftforge/forge").isDirectory()) {
            type = "forge";
            launch = Software.forgeLaunch(root);
        } else if (new File(root, "server.jar").isFile()) {
            // our installer names every jar server.jar; Bukkit-family servers leave their own config files
            type = new File(root, "purpur.yml").isFile() ? "purpur" : new File(root, "bukkit.yml").isFile() ? "paper" : "vanilla";
            launch = "-jar server.jar";
        } else {
            throw new IOException("Jenis server di zip ini tidak dikenali");
        }
        String name = zipName.replaceAll("(?i)\\.zip$", "").replaceAll("[\\r\\n\\t]", " ").trim();
        m.set(Config.NAME, name.isEmpty() ? "Server" : name.length() > 40 ? name.substring(0, 40) : name);
        m.set(Config.TYPE, type);
        m.set(Config.LAUNCH, launch);
        m.set(Config.RAM_MB, "3072");
        m.set(Config.JAVA, "21");
        m.set("created", String.valueOf(System.currentTimeMillis()));
    }

    private static String jarStarting(File dir, String prefix) {
        File[] f = dir.listFiles((d, n) -> n.startsWith(prefix) && n.endsWith(".jar"));
        return f == null || f.length == 0 ? null : f[0].getName();
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

    /** Bahasa panel ("en" default, "id"); dipakai untuk teks yang tidak lewat panel (notifikasi). */
    public static volatile String lang = "en";

    public static String tr(String en, String id) {
        return "id".equals(lang) ? id : en;
    }

    public String summary() {
        if (!task.isEmpty()) return task + "...";
        if (sleeping()) return srv.get(Config.NAME, "Server") + tr(" asleep, join to wake it up", " tidur, join untuk membangunkan");
        String s = mc.state();
        switch (s) {
            case "running":
                return srv.get(Config.NAME, "Server") + tr(" running, ", " jalan, ") + mc.players().size() + tr(" players online", " pemain online");
            case "starting":
                return tr("Server starting...", "Server sedang menyala...");
            case "stopping":
                return tr("Server stopping...", "Server sedang mati...");
            case "crashed":
                return tr("Server crashed", "Server crash");
            default:
                return tr("Server stopped, panel active", "Server mati, panel aktif");
        }
    }

    public void summaryChanged() {
        try {
            platform.onSummary(summary());
        } catch (Throwable ignored) {
        }
    }

    public void shutdown(long waitMs) {
        endSleep();
        playit.stop();
        if (mc.alive()) mc.stopAndWait(waitMs);
        tasks.shutdownNow();
        timer.shutdownNow();
    }
}
