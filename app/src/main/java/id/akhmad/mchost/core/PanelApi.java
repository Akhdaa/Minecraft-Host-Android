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
    private static final Set<String> PROXY_HOSTS = new HashSet<>(Arrays.asList("meta.fabricmc.net", "api.modrinth.com"));
    private static final long MAX_UPLOAD = 2L * 1024 * 1024 * 1024;

    private final Host host;
    private final Set<String> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final SecureRandom random = new SecureRandom();

    public PanelApi(Host host) {
        this.host = host;
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
        switch (p) {
            case "/api/status": status(req, res); return;
            case "/api/log": log(req, res); return;
            case "/api/start": requirePost(post); host.mc.start(); res.ok(); return;
            case "/api/stop": requirePost(post); host.mc.stop(); res.ok(); return;
            case "/api/restart": requirePost(post); host.mc.restart(); res.ok(); return;
            case "/api/kill": requirePost(post); host.mc.kill(); res.ok(); return;
            case "/api/command": requirePost(post); command(req, res); return;
            case "/api/settings": settings(req, res, post); return;
            case "/api/properties": properties(req, res, post); return;
            case "/api/mods": mods(res); return;
            case "/api/mods/upload": requirePost(post); upload(req, res, new File(host.serverDir, "mods"), ".jar"); return;
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
            case "/api/world/reset": requirePost(post); host.resetWorld(); res.ok(); return;
            case "/api/playit/start": requirePost(post); host.playit.start(); res.ok(); return;
            case "/api/playit/stop": requirePost(post); host.playit.stop(); res.ok(); return;
            case "/api/playit/tunnels": playitTunnels(req, res); return;
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

    private void login(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        if (!"POST".equals(req.method)) {
            res.json(200, Json.obj().put("authorized", authorized(req)).toString());
            return;
        }
        String pin = req.param("pin", "").trim();
        if (!pin.equals(host.cfg.get(Config.PIN, ""))) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            res.error(403, "PIN salah");
            return;
        }
        byte[] b = new byte[24];
        random.nextBytes(b);
        StringBuilder tok = new StringBuilder();
        for (byte x : b) tok.append(String.format("%02x", x));
        sessions.add(tok.toString());
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
                .put("runtimeReady", host.runtimeReady())
                .put("installed", host.mc.installed())
                .put("mcVersion", host.cfg.get(Config.MC_VERSION, ""))
                .put("loader", host.cfg.get(Config.LOADER, ""))
                .raw("players", Json.strArray(host.mc.players()))
                .put("uptime", host.mc.uptimeSec())
                .put("rssMb", host.mc.rssMb())
                .put("ramMb", host.cfg.getInt(Config.RAM_MB, 3072))
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
            String ram = req.param("ram");
            if (ram != null) {
                int r;
                try {
                    r = Integer.parseInt(ram.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("RAM harus angka (MB)");
                }
                long total = deviceRamMb();
                long max = total > 0 ? total : 131072;
                if (r < 256 || r > max) throw new IllegalArgumentException("RAM harus 256 sampai " + max + " MB");
                c.set(Config.RAM_MB, String.valueOf(r));
            }
            String jvm = req.param("jvmArgs");
            if (jvm != null) c.set(Config.JVM_ARGS, jvm.replace('\n', ' ').trim());
            for (String[] b : new String[][]{{"autoRestart", Config.AUTO_RESTART}, {"autoStart", Config.AUTO_START},
                    {"bootStart", Config.BOOT_START}, {"eula", Config.EULA}, {"playitAutostart", Config.PLAYIT_AUTOSTART}}) {
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
            if (domain != null) {
                domain = domain.trim().toLowerCase(Locale.US).replaceAll("^[a-z]+://", "").replaceAll("/.*$", "");
                if (!domain.isEmpty() && !domain.matches("[a-z0-9.\\-]{3,253}(:[0-9]{1,5})?")) {
                    throw new IllegalArgumentException("Domain tidak valid, contoh: mc.domainkamu.com");
                }
                c.set(Config.CUSTOM_DOMAIN, domain);
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
                .put("ram", c.getInt(Config.RAM_MB, 3072))
                .put("deviceRamMb", deviceRamMb())
                .put("jvmArgs", c.get(Config.JVM_ARGS, ""))
                .put("autoRestart", c.getBool(Config.AUTO_RESTART))
                .put("autoStart", c.getBool(Config.AUTO_START))
                .put("bootStart", c.getBool(Config.BOOT_START))
                .put("eula", c.getBool(Config.EULA))
                .put("playitAutostart", c.getBool(Config.PLAYIT_AUTOSTART))
                .put("hasPlayitSecret", !c.get(Config.PLAYIT_SECRET, "").isEmpty())
                .put("customDomain", c.get(Config.CUSTOM_DOMAIN, ""))
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

    // ------------------------------------------------------------ mods

    private void mods(MiniHttp.Res res) throws IOException {
        File dir = new File(host.serverDir, "mods");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".jar") || n.endsWith(".jar.disabled"));
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
        res.json(200, Json.obj().raw("mods", arr.toString()).put("needsRestart", host.mc.alive()).toString());
    }

    private File modFile(MiniHttp.Req req) {
        String name = req.param("name", "");
        if (!IO.safeName(name) || !(name.endsWith(".jar") || name.endsWith(".jar.disabled"))) {
            throw new IllegalArgumentException("Nama file mod tidak valid");
        }
        File f = new File(new File(host.serverDir, "mods"), name);
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
        if (!IO.safeName(name) || !name.endsWith(".jar")) throw new IllegalArgumentException("Nama file tidak valid");
        File dest = new File(new File(host.serverDir, "mods"), name);
        IO.download(url, dest, null);
        host.mcLog.panel("Mod terpasang: " + name);
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
                .put("worldExists", host.worldDir().exists()).toString());
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
        res.ok();
    }

    // ------------------------------------------------------------ fabric / proxy

    private void install(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        host.installFabric(req.param("mc", "1.20.1").trim(), req.param("loader", "").trim(), req.param("installer", "").trim());
        res.ok();
    }

    private void proxy(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        String target = req.param("u", "");
        URL u = new URL(target);
        if (!"https".equals(u.getProtocol()) || !PROXY_HOSTS.contains(u.getHost())) {
            throw new IllegalArgumentException("Host tidak diizinkan");
        }
        byte[] body = IO.fetch(target, 8 * 1024 * 1024);
        res.send(200, "application/json; charset=utf-8", body);
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
