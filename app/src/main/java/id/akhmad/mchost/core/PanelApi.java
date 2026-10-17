package id.akhmad.mchost.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** REST API + static files for the web panel. */
public class PanelApi implements MiniHttp.Handler {
    private static final String COOKIE = "mch_session";
    private static final Set<String> PROXY_HOSTS = new HashSet<>(Arrays.asList("meta.fabricmc.net", "api.modrinth.com",
            "piston-meta.mojang.com", "fill.papermc.io", "api.purpurmc.org", "files.minecraftforge.net", "maven.neoforged.net"));
    /** Endpoints that work without any server (everything else acts on the active server). */
    private static final Set<String> GLOBAL = new HashSet<>(Arrays.asList("/api/status", "/api/log", "/api/settings",
            "/api/proxy", "/api/playit/start", "/api/playit/stop", "/api/playit/tunnels", "/api/domain/info",
            "/api/domain/sync", "/api/logout", "/api/servers", "/api/servers/create", "/api/servers/select",
            "/api/servers/update", "/api/servers/delete"));
    private static final long MAX_UPLOAD = 2L * 1024 * 1024 * 1024;

    private final Host host;
    private final Set<String> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final SecureRandom random = new SecureRandom();
    private final FileApi files;

    public PanelApi(Host host) {
        this.host = host;
        this.files = new FileApi(host);
    }

    @Override
    public void handle(MiniHttp.Req req, MiniHttp.Res res) throws Exception {
        String p = req.path;
        if (!p.startsWith("/api/")) {
            serveStatic(p, res);
            return;
        }
        if (p.equals("/api/login")) {
            login(req, res);
            return;
        }
        if (!authorized(req)) {
            res.error(401, "Butuh login");
            return;
        }
        boolean post = "POST".equals(req.method) || "PUT".equals(req.method);
        if (!post && !"GET".equals(req.method)) {
            res.error(405, "Method tidak didukung");
            return;
        }
        // Mutating calls must be POST/PUT and come from our own page (simple CSRF guard).
        if (post && !"1".equals(req.header("x-panel"))) {
            res.error(403, "Header X-Panel hilang");
            return;
        }
        if (!host.hasServer() && !GLOBAL.contains(p)) {
            res.error(409, "Belum ada server. Buat server dulu.");
            return;
        }
        if (files.handles(p)) {
            files.handle(p, req, res, post);
            return;
        }
        switch (p) {
            case "/api/status": status(req, res); return;
            case "/api/log": log(req, res); return;
            case "/api/servers": servers(res); return;
            case "/api/servers/create": requirePost(post); serverCreate(req, res); return;
            case "/api/servers/select": requirePost(post); host.selectServer(req.param("id", "")); files.invalidate(); res.ok(); return;
            case "/api/servers/update": requirePost(post); serverUpdate(req, res); return;
            case "/api/servers/delete": requirePost(post); host.deleteServer(req.param("id", "")); files.invalidate(); res.ok(); return;
            case "/api/start": requirePost(post); host.startServer(); res.ok(); return;
            case "/api/stop": requirePost(post); host.mc.stop(); res.ok(); return;
            case "/api/restart": requirePost(post); host.mc.restart(); res.ok(); return;
            case "/api/kill": requirePost(post); host.mc.kill(); res.ok(); return;
            case "/api/command": requirePost(post); command(req, res); return;
            case "/api/settings": settings(req, res, post); return;
            case "/api/properties": properties(req, res, post); return;
            case "/api/mods": mods(res); return;
            case "/api/players": players(res); return;
            case "/api/players/action": requirePost(post); playerAction(req, res); return;
            case "/api/mods/upload": requirePost(post); upload(req, res, addonDir(), ".jar"); return;
            case "/api/mods/delete": requirePost(post); modDelete(req, res); return;
            case "/api/mods/toggle": requirePost(post); modToggle(req, res); return;
            case "/api/mods/install": requirePost(post); modInstall(req, res); return;
            case "/api/proxy": proxy(req, res); return;
            case "/api/install": requirePost(post); install(req, res); return;
            case "/api/backups": backups(res); return;
            case "/api/backups/create": requirePost(post); host.createBackup(); res.ok(); return;
            case "/api/backups/download": backupDownload(req, res); return;
            case "/api/backups/delete": requirePost(post); backupDelete(req, res); return;
            case "/api/backups/restore": requirePost(post); host.restoreBackup(req.param("name", "")); res.ok(); return;
            case "/api/backups/upload": requirePost(post); upload(req, res, host.backupDir, ".zip"); return;
            case "/api/world/reset": requirePost(post); host.resetWorld(); files.invalidate(); res.ok(); return;
            case "/api/world/reset-dim": requirePost(post); host.resetDimension(req.param("dim", "")); files.invalidate(); res.ok(); return;
            case "/api/playit/start": requirePost(post); host.playit.start(); res.ok(); return;
            case "/api/playit/stop": requirePost(post); host.playit.stop(); res.ok(); return;
            case "/api/playit/tunnels": playitTunnels(req, res); return;
            case "/api/domain/info": domainInfo(req, res); return;
            case "/api/domain/sync": requirePost(post); domainSync(req, res); return;
            case "/api/logout": requirePost(post); logout(req, res); return;
            default: res.error(404, "Endpoint tidak ada");
        }
    }

    private static void requirePost(boolean post) {
        if (!post) throw new IllegalArgumentException("Pakai POST");
    }

    // ------------------------------------------------------------ auth

    private boolean authorized(MiniHttp.Req req) {
        if (req.isLocal()) return true;
        String c = req.cookie(COOKIE);
        return c != null && sessions.contains(c);
    }

    // Brute-force guard: 3 wrong PINs per IP -> cooldown that doubles each time (30 s .. 15 min),
    // plus a global lock if many IPs fail in a short time.
    private static final int MAX_TRIES = 3;
    private static final class Guard {
        int fails;
        int level;
        long lockUntil;
    }
    private final java.util.Map<String, Guard> guards = new ConcurrentHashMap<>();
    private final java.util.ArrayDeque<Long> recentFails = new java.util.ArrayDeque<>();
    private volatile long globalLockUntil;

    private static String ipOf(MiniHttp.Req req) {
        return req.remote == null ? "?" : req.remote.getHostAddress();
    }

    private long lockLeftSec(String ip) {
        long now = System.currentTimeMillis();
        Guard g = guards.get(ip);
        long until = Math.max(globalLockUntil, g == null ? 0 : g.lockUntil);
        return until > now ? (until - now + 999) / 1000 : 0;
    }

    private int triesLeft(String ip) {
        Guard g = guards.get(ip);
        return MAX_TRIES - (g == null ? 0 : g.fails);
    }

    private Json loginState(MiniHttp.Req req) {
        String ip = ipOf(req);
        return Json.obj().put("authorized", authorized(req)).put("retryAfter", lockLeftSec(ip)).put("triesLeft", triesLeft(ip));
    }

    private void login(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        if (!"POST".equals(req.method)) {
            res.json(200, loginState(req).toString());
            return;
        }
        String ip = ipOf(req);
        long wait = lockLeftSec(ip);
        if (wait > 0) {
            res.json(429, loginState(req).put("ok", false).put("error", "Terlalu banyak percobaan. Coba lagi dalam " + wait + " detik.").toString());
            return;
        }
        String pin = req.param("pin", "").trim();
        if (!pin.equals(host.cfg.get(Config.PIN, ""))) {
            long now = System.currentTimeMillis();
            Guard g = guards.computeIfAbsent(ip, k -> new Guard());
            String msg;
            synchronized (g) {
                g.fails++;
                if (g.fails >= MAX_TRIES) {
                    g.level++;
                    long secs = Math.min(15 * 60, 30L << Math.min(g.level - 1, 5));
                    g.lockUntil = now + secs * 1000;
                    g.fails = 0;
                    msg = "PIN salah 3x. Dikunci " + secs + " detik.";
                    host.mcLog.panel("Login panel dari " + ip + " dikunci " + secs + " detik (PIN salah 3x).");
                } else {
                    msg = "PIN salah. Sisa " + (MAX_TRIES - g.fails) + " percobaan.";
                }
            }
            synchronized (recentFails) {
                recentFails.addLast(now);
                while (!recentFails.isEmpty() && now - recentFails.peekFirst() > 10 * 60_000) recentFails.removeFirst();
                if (recentFails.size() >= 30) {
                    globalLockUntil = now + 5 * 60_000;
                    recentFails.clear();
                    host.mcLog.panel("Terlalu banyak login gagal dari berbagai alamat, login jarak jauh dikunci 5 menit.");
                }
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            int code = lockLeftSec(ip) > 0 ? 429 : 403;
            res.json(code, loginState(req).put("ok", false).put("error", msg).toString());
            return;
        }
        guards.remove(ip);
        byte[] b = new byte[24];
        random.nextBytes(b);
        StringBuilder tok = new StringBuilder();
        for (byte x : b) tok.append(String.format("%02x", x));
        sessions.add(tok.toString());
        host.mcLog.panel("Login panel berhasil dari " + ip);
        res.header("Set-Cookie", COOKIE + "=" + tok + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=2592000");
        res.ok();
    }

    private void logout(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String c = req.cookie(COOKIE);
        if (c != null) sessions.remove(c);
        res.header("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0");
        res.ok();
    }

    // ------------------------------------------------------------ status & log

    private void status(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        Json j = Json.obj()
                .put("state", host.mc.state())
                .put("task", host.task())
                .put("taskError", host.taskError())
                .put("taskProgress", host.progress())
                .put("runtimeReady", host.runtimeReady())
                .put("installed", host.mc.installed())
                .put("serverId", host.activeId())
                .put("serverName", host.srv.get(Config.NAME, ""))
                .put("type", host.hasServer() ? host.type() : "")
                .put("mcVersion", host.srv.get(Config.MC_VERSION, ""))
                .put("loader", host.srv.get(Config.LOADER, ""))
                .put("java", host.javaMajor())
                .raw("players", Json.strArray(host.mc.players()))
                .put("uptime", host.mc.uptimeSec())
                .put("rssMb", host.mc.rssMb())
                .put("ramMb", host.srv.getInt(Config.RAM_MB, 3072))
                .put("deviceRamMb", deviceRamMb())
                .put("eula", host.cfg.getBool(Config.EULA))
                .raw("lan", Json.strArray(lanIps()))
                .put("panelPort", host.cfg.getInt(Config.PORT, 8080))
                .put("mcPort", host.property("server-port", "25565"))
                .put("maxPlayers", host.property("max-players", "20"))
                .put("customDomain", host.cfg.get(Config.CUSTOM_DOMAIN, ""))
                .raw("battery", host.platform.batteryJson())
                .raw("playit", Json.obj().put("available", host.playit.available())
                        .put("state", host.playit.alive() ? "running" : host.playit.state())
                        .put("hasSecret", !host.cfg.get(Config.PLAYIT_SECRET, "").isEmpty()).toString())
                .put("local", req.isLocal());
        if (req.isLocal()) j.put("pin", host.cfg.get(Config.PIN, ""));
        res.json(200, j.toString());
    }

    private void log(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        LogBuffer buf = "tunnel".equals(req.param("src")) ? host.tunnelLog : host.mcLog;
        long since;
        try {
            since = Long.parseLong(req.param("since", "0"));
        } catch (NumberFormatException e) {
            since = 0;
        }
        StringBuilder lines = new StringBuilder("[");
        boolean first = true;
        for (LogBuffer.Line l : buf.since(since, 600)) {
            if (!first) lines.append(',');
            lines.append(Json.str(l.text));
            first = false;
        }
        lines.append(']');
        res.json(200, Json.obj().put("next", buf.last()).raw("lines", lines.toString()).toString());
    }

    private void command(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String cmd = req.param("cmd", "").trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) throw new IllegalArgumentException("Command kosong");
        if (cmd.contains("\n")) throw new IllegalArgumentException("Satu baris saja");
        if (!host.mc.alive()) throw new IllegalStateException("Server belum jalan");
        host.mcLog.add("> " + cmd);
        host.mc.send(cmd);
        res.ok();
    }

    // ------------------------------------------------------------ settings

    private void settings(MiniHttp.Req req, MiniHttp.Res res, boolean post) throws IOException {
        Config c = host.cfg;
        if (post) {
            for (String[] b : new String[][]{{"autoRestart", Config.AUTO_RESTART}, {"autoStart", Config.AUTO_START},
                    {"bootStart", Config.BOOT_START}, {"eula", Config.EULA}, {"playitAutostart", Config.PLAYIT_AUTOSTART},
                    {"playitWithServer", Config.PLAYIT_WITH_SERVER}}) {
                String v = req.param(b[0]);
                if (v != null) c.set(b[1], String.valueOf("true".equals(v)));
            }
            String secret = req.param("playitSecret");
            if (secret != null && !secret.trim().isEmpty()) {
                if (!secret.trim().matches("[A-Za-z0-9_\\-]{16,200}")) throw new IllegalArgumentException("Format secret key aneh");
                c.set(Config.PLAYIT_SECRET, secret.trim());
            }
            if ("true".equals(req.param("clearPlayitSecret"))) c.set(Config.PLAYIT_SECRET, "");
            String domain = req.param("customDomain");
            if (domain != null) c.set(Config.CUSTOM_DOMAIN, Cloudflare.cleanHost(domain));
            String cf = req.param("cfToken");
            if (cf != null && !cf.trim().isEmpty()) {
                if (!cf.trim().matches("[A-Za-z0-9_\\-]{20,200}")) throw new IllegalArgumentException("Format API token Cloudflare aneh");
                c.set(Config.CF_TOKEN, cf.trim());
            }
            if ("true".equals(req.param("clearCfToken"))) c.set(Config.CF_TOKEN, "");
            String auto = req.param("cfAutosync");
            if (auto != null) c.set(Config.CF_AUTOSYNC, String.valueOf("true".equals(auto)));
            String abh = req.param("autoBackupHours");
            if (abh != null) {
                int h = Integer.parseInt(abh.trim());
                if (h < 0 || h > 168) throw new IllegalArgumentException("Interval backup 0 sampai 168 jam");
                c.set(Config.AUTO_BACKUP_HOURS, String.valueOf(h));
            }
            String keep = req.param("backupKeep");
            if (keep != null) {
                int k = Integer.parseInt(keep.trim());
                if (k < 1 || k > 50) throw new IllegalArgumentException("Simpan 1 sampai 50 backup");
                c.set(Config.BACKUP_KEEP, String.valueOf(k));
            }
            String pin = req.param("pin");
            if (pin != null && !pin.trim().isEmpty()) {
                if (!req.isLocal()) throw new IllegalStateException("PIN hanya bisa diganti dari aplikasi di HP");
                if (!pin.trim().matches("[0-9]{4,12}")) throw new IllegalArgumentException("PIN harus 4 sampai 12 angka");
                c.set(Config.PIN, pin.trim());
                sessions.clear();
            }
            c.save();
        }
        res.json(200, Json.obj()
                .put("deviceRamMb", deviceRamMb())
                .put("autoRestart", c.getBool(Config.AUTO_RESTART))
                .put("autoStart", c.getBool(Config.AUTO_START))
                .put("bootStart", c.getBool(Config.BOOT_START))
                .put("eula", c.getBool(Config.EULA))
                .put("playitAutostart", c.getBool(Config.PLAYIT_AUTOSTART))
                .put("playitWithServer", c.getBool(Config.PLAYIT_WITH_SERVER))
                .put("hasPlayitSecret", !c.get(Config.PLAYIT_SECRET, "").isEmpty())
                .put("customDomain", c.get(Config.CUSTOM_DOMAIN, ""))
                .put("hasCfToken", !c.get(Config.CF_TOKEN, "").isEmpty())
                .put("cfAutosync", c.getBool(Config.CF_AUTOSYNC))
                .toString());
    }

    private void properties(MiniHttp.Req req, MiniHttp.Res res, boolean post) throws IOException {
        File f = new File(host.serverDir, "server.properties");
        if (post) {
            String text = req.bodyText(256 * 1024);
            IO.writeText(f, text.replace("\r\n", "\n"));
            res.ok();
            return;
        }
        res.text(200, IO.readText(f));
    }

    // ------------------------------------------------------------ players

    private static final java.util.regex.Pattern NAME_FIELD = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_.]{1,20})\"");
    private static final String PLAYER_NAME = "[A-Za-z0-9_.]{2,20}";

    private List<String> namesIn(String file) {
        List<String> out = new ArrayList<>();
        try {
            java.util.regex.Matcher m = NAME_FIELD.matcher(IO.readText(new File(host.serverDir, file)));
            while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        } catch (IOException ignored) {
        }
        return out;
    }

    private void players(MiniHttp.Res res) throws IOException {
        List<String> online = host.mc.players();
        List<String> ops = namesIn("ops.json"), wl = namesIn("whitelist.json"), banned = namesIn("banned-players.json");
        java.util.TreeSet<String> all = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        all.addAll(online);
        all.addAll(ops);
        all.addAll(wl);
        all.addAll(banned);
        all.addAll(namesIn("usercache.json"));
        res.json(200, Json.obj()
                .raw("all", Json.strArray(all))
                .raw("online", Json.strArray(online))
                .raw("ops", Json.strArray(ops))
                .raw("whitelist", Json.strArray(wl))
                .raw("banned", Json.strArray(banned))
                .put("whitelistOn", "true".equals(host.property("white-list", "false")))
                .put("running", "running".equals(host.mc.state()))
                .toString());
    }

    /** Whitelisted, structured player actions translated into server commands. */
    private void playerAction(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String action = req.param("action", "");
        String name = req.param("name", "").trim();
        String cmd;
        switch (action) {
            case "whitelist-on": cmd = "whitelist on"; break;
            case "whitelist-off": cmd = "whitelist off"; break;
            default:
                if (!name.matches(PLAYER_NAME)) throw new IllegalArgumentException("Nama pemain tidak valid");
                switch (action) {
                    case "op": cmd = "op " + name; break;
                    case "deop": cmd = "deop " + name; break;
                    case "wl-add": cmd = "whitelist add " + name; break;
                    case "wl-remove": cmd = "whitelist remove " + name; break;
                    case "kick": cmd = "kick " + name; break;
                    case "ban": cmd = "ban " + name; break;
                    case "pardon": cmd = "pardon " + name; break;
                    case "survival": case "creative": case "adventure": case "spectator":
                        cmd = "gamemode " + action + " " + name; break;
                    case "heal": cmd = "effect give " + name + " minecraft:instant_health 1 10"; break;
                    case "feed": cmd = "effect give " + name + " minecraft:saturation 1 20"; break;
                    default: throw new IllegalArgumentException("Aksi tidak dikenal");
                }
        }
        if (!"running".equals(host.mc.state())) throw new IllegalStateException("Nyalakan server dulu, aksi pemain dijalankan lewat command server");
        host.mcLog.add("> " + cmd);
        host.mc.send(cmd);
        res.json(200, Json.obj().put("ok", true).put("command", cmd).toString());
    }

    // ------------------------------------------------------------ servers

    private static int parseRam(String ram) {
        int r;
        try {
            r = Integer.parseInt(ram.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("RAM harus angka (MB)");
        }
        long total = deviceRamMb();
        long max = total > 0 ? total : 131072;
        if (r < 256 || r > max) throw new IllegalArgumentException("RAM harus 256 sampai " + max + " MB");
        return r;
    }

    private void servers(MiniHttp.Res res) throws IOException {
        StringBuilder arr = new StringBuilder("[");
        for (String id : host.serverIds()) {
            Config m = host.meta(id);
            if (arr.length() > 1) arr.append(',');
            String type = m.get(Config.TYPE, "fabric");
            String addons = Software.addonDir(type);
            String[] list = addons == null ? null : new File(new File(host.serversRoot, id), addons).list((d, n) -> n.endsWith(".jar"));
            arr.append(Json.obj().put("id", id).put("name", m.get(Config.NAME, id)).put("type", type)
                    .put("mcVersion", m.get(Config.MC_VERSION, "")).put("loader", m.get(Config.LOADER, ""))
                    .put("installed", !m.get(Config.LAUNCH, "").isEmpty()).put("active", id.equals(host.activeId()))
                    .put("ramMb", m.getInt(Config.RAM_MB, 3072)).put("jvmArgs", m.get(Config.JVM_ARGS, ""))
                    .put("java", m.getInt(Config.JAVA, 0)).put("addons", list == null ? 0 : list.length)
                    .put("created", m.get("created", "0")).toString());
        }
        arr.append(']');
        res.json(200, Json.obj().raw("servers", arr.toString()).put("active", host.activeId())
                .put("deviceRamMb", deviceRamMb()).toString());
    }

    private void serverCreate(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String id = host.createServer(req.param("name", ""), req.param("type", ""), req.param("mc", "").trim(),
                req.param("loader", "").trim(), parseRam(req.param("ram", "3072")));
        files.invalidate();
        res.json(200, Json.obj().put("ok", true).put("id", id).toString());
    }

    /** Name, RAM and JVM args of any server (they apply on its next start). */
    private void serverUpdate(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        Config m = host.meta(req.param("id", ""));
        String name = req.param("name");
        if (name != null) m.set(Config.NAME, Host.cleanName(name));
        String ram = req.param("ram");
        if (ram != null) m.set(Config.RAM_MB, String.valueOf(parseRam(ram)));
        String jvm = req.param("jvmArgs");
        if (jvm != null) m.set(Config.JVM_ARGS, jvm.replace('\n', ' ').trim());
        m.save();
        host.summaryChanged();
        res.ok();
    }

    // ------------------------------------------------------------ mods / plugins

    /** mods/ or plugins/ of the active server, depending on its software. */
    private File addonDir() {
        String d = Software.addonDir(host.type());
        if (d == null) throw new IllegalStateException("Server Vanilla tidak memakai mod atau plugin, cuma datapack");
        return new File(host.serverDir, d);
    }

    private void mods(MiniHttp.Res res) throws IOException {
        String d = Software.addonDir(host.type());
        File dir = new File(host.serverDir, d == null ? "mods" : d);
        File[] files = d == null ? null : dir.listFiles((x, n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
        List<File> list = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            if (i > 0) arr.append(',');
            arr.append(Json.obj().put("name", f.getName()).put("size", f.length())
                    .put("enabled", f.getName().endsWith(".jar")).toString());
        }
        arr.append(']');
        String[] packs = new File(host.worldDir(), "datapacks").list();
        res.json(200, Json.obj().raw("mods", arr.toString())
                .raw("datapacks", Json.strArray(packs == null ? Collections.<String>emptyList() : Arrays.asList(packs)))
                .put("dir", d == null ? "" : d)
                .put("needsRestart", host.mc.alive()).toString());
    }

    private File modFile(MiniHttp.Req req) {
        String name = req.param("name", "");
        if (!IO.safeName(name) || !(name.endsWith(".jar") || name.endsWith(".jar.disabled"))) {
            throw new IllegalArgumentException("Nama file mod tidak valid");
        }
        File f = new File(addonDir(), name);
        if (!f.exists()) throw new IllegalArgumentException("Mod tidak ditemukan");
        return f;
    }

    private void modDelete(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        if (!modFile(req).delete()) throw new IOException("Gagal hapus");
        res.ok();
    }

    private void modToggle(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = modFile(req);
        String n = f.getName();
        File to = new File(f.getParentFile(), n.endsWith(".disabled") ? n.substring(0, n.length() - 9) : n + ".disabled");
        if (!f.renameTo(to)) throw new IOException("Gagal rename");
        res.ok();
    }

    private void modInstall(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String url = req.param("url", "");
        String name = req.param("filename", "");
        URL u = new URL(url);
        if (!"https".equals(u.getProtocol()) || !"cdn.modrinth.com".equals(u.getHost())) {
            throw new IllegalArgumentException("Hanya file dari cdn.modrinth.com");
        }
        // datapacks are new zips in <world>/datapacks, so they are safe to add while the server runs (picked up on /reload)
        boolean pack = "datapack".equals(req.param("kind", ""));
        if (!IO.safeName(name) || !name.endsWith(pack ? ".zip" : ".jar")) throw new IllegalArgumentException("Nama file tidak valid");
        File dir = pack ? new File(host.worldDir(), "datapacks") : addonDir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Gagal membuat folder " + dir.getName());
        IO.download(url, new File(dir, name), null);
        if (pack) files.invalidate();
        host.mcLog.panel((pack ? "Datapack" : "Mod") + " terpasang: " + name);
        res.ok();
    }

    // ------------------------------------------------------------ uploads/backups

    private void upload(MiniHttp.Req req, MiniHttp.Res res, File dir, String ext) throws IOException {
        String name = req.param("name", "");
        if (!IO.safeName(name) || !name.toLowerCase(Locale.US).endsWith(ext)) {
            throw new IllegalArgumentException("File harus " + ext);
        }
        if (req.contentLength() <= 0) throw new IllegalArgumentException("File kosong");
        if (req.contentLength() > MAX_UPLOAD) throw new IllegalArgumentException("File terlalu besar");
        dir.mkdirs();
        File tmp = new File(dir, ".upload-" + System.nanoTime());
        File dest = new File(dir, name);
        try (InputStream in = req.body(); OutputStream out = new FileOutputStream(tmp)) {
            long n = IO.copy(in, out);
            if (n != req.contentLength()) throw new IOException("Upload terputus");
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (dest.exists() && !dest.delete()) throw new IOException("Gagal menimpa file lama");
        if (!tmp.renameTo(dest)) throw new IOException("Gagal menyimpan");
        host.mcLog.panel("Upload selesai: " + name);
        res.ok();
    }

    private void backups(MiniHttp.Res res) throws IOException {
        StringBuilder arr = new StringBuilder("[");
        boolean first = true;
        for (File f : host.backups()) {
            if (!first) arr.append(',');
            arr.append(Json.obj().put("name", f.getName()).put("size", f.length()).put("time", f.lastModified()).toString());
            first = false;
        }
        arr.append(']');
        res.json(200, Json.obj().raw("backups", arr.toString()).put("world", host.worldDir().getName())
                .put("worldExists", host.worldDir().exists())
                .put("running", host.mc.alive())
                .put("autoHours", host.cfg.getInt(Config.AUTO_BACKUP_HOURS, 0))
                .put("keep", host.cfg.getInt(Config.BACKUP_KEEP, 5))
                .put("lastAuto", host.lastAutoBackup())
                .toString());
    }

    private File backupFile(MiniHttp.Req req) {
        String name = req.param("name", "");
        File f = new File(host.backupDir, name);
        if (!IO.safeName(name) || !name.endsWith(".zip") || !f.exists()) throw new IllegalArgumentException("Backup tidak ditemukan");
        return f;
    }

    private void backupDownload(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = backupFile(req);
        res.file(f, "application/zip", f.getName());
    }

    private void backupDelete(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        if (!backupFile(req).delete()) throw new IOException("Gagal hapus");
        files.invalidate();
        res.ok();
    }

    // ------------------------------------------------------------ install / proxy

    private void install(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        host.install(req.param("mc", "").trim(), req.param("loader", "").trim());
        res.ok();
    }

    private void proxy(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String target = req.param("u", "");
        URL u = new URL(target);
        if (!"https".equals(u.getProtocol()) || !PROXY_HOSTS.contains(u.getHost())) {
            throw new IllegalArgumentException("Host tidak diizinkan");
        }
        byte[] body = IO.fetch(target, 8 * 1024 * 1024);
        // NeoForge's version list is maven XML; everything else is JSON
        boolean xml = body.length > 0 && body[0] == '<';
        res.send(200, xml ? "text/xml; charset=utf-8" : "application/json; charset=utf-8", body);
    }

    private void playitTunnels(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String json;
        try {
            json = host.playit.tunnelsJson("1".equals(req.param("force")));
        } catch (IOException e) {
            res.error(502, "Gagal hubungi playit.gg: " + e.getMessage());
            return;
        }
        if (json == null) {
            res.json(200, "{\"status\":\"no-secret\"}");
            return;
        }
        res.json(200, json);
    }

    private void domainInfo(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String domain = host.cfg.get(Config.CUSTOM_DOMAIN, "");
        String addr = req.param("addr", "").trim();
        Json j = Json.obj().put("domain", domain)
                .put("srvName", domain.isEmpty() ? "" : Cloudflare.srvName(domain))
                .put("hasToken", !host.cfg.get(Config.CF_TOKEN, "").isEmpty())
                .put("autosync", host.cfg.getBool(Config.CF_AUTOSYNC))
                .put("tunnel", addr);
        Cloudflare.Srv expected = null, current = null;
        String err = "";
        if (!addr.isEmpty()) {
            try {
                expected = Cloudflare.targetFor(addr);
            } catch (Exception e) {
                err = "Gagal membaca alamat playit: " + e.getMessage();
            }
        }
        if (!domain.isEmpty()) {
            try {
                current = Cloudflare.lookup(domain);
            } catch (Exception e) {
                err = "Gagal cek DNS: " + e.getMessage();
            }
        }
        j.raw("expected", expected == null ? "null" : expected.json())
                .raw("current", current == null ? "null" : current.json())
                .put("match", expected != null && expected.sameTarget(current))
                .put("error", err);
        res.json(200, j.toString());
    }

    private void domainSync(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String domain = host.cfg.get(Config.CUSTOM_DOMAIN, "");
        String token = host.cfg.get(Config.CF_TOKEN, "");
        if (domain.isEmpty()) throw new IllegalArgumentException("Isi domain dulu");
        if (token.isEmpty()) throw new IllegalArgumentException("Isi API token Cloudflare dulu");
        String addr = req.param("addr", "").trim();
        if (addr.isEmpty()) throw new IllegalArgumentException("Belum ada alamat tunnel playit");
        Cloudflare.Srv target = Cloudflare.targetFor(addr);
        String msg = Cloudflare.upsert(domain, target, token);
        host.tunnelLog.panel(msg + ": " + Cloudflare.srvName(domain) + " -> " + target.target + ":" + target.port);
        res.json(200, Json.obj().put("ok", true).put("message", msg).raw("record", target.json()).toString());
    }

    // ------------------------------------------------------------ static

    private void serveStatic(String path, MiniHttp.Res res) throws IOException {
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        String rel = path.substring(1);
        if (rel.contains("..") || rel.contains("\\")) {
            res.error(400, "bad path");
            return;
        }
        try (InputStream in = host.platform.openAsset(rel)) {
            if (in == null) {
                res.error(404, "Not found");
                return;
            }
            byte[] b = IO.readAll(in, 16 * 1024 * 1024);
            res.send(200, mime(rel), b);
        }
    }

    private static String mime(String n) {
        n = n.toLowerCase(Locale.US);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    /** Total physical RAM in MB from /proc/meminfo, or -1 if unknown. */
    public static long deviceRamMb() {
        try {
            for (String l : IO.readText(new java.io.File("/proc/meminfo")).split("\n")) {
                if (l.startsWith("MemTotal:")) return Long.parseLong(l.replaceAll("[^0-9]", "")) / 1024;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    // ------------------------------------------------------------ network

    public static List<String> lanIps() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            if (ifs == null) return out;
            for (NetworkInterface ni : Collections.list(ifs)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("dummy") || n.startsWith("tun")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
