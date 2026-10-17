package id.akhmad.mchost.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** File browser for the server folder (/api/files/*) and world size info (/api/world/info). */
public class FileApi {
    private static final Set<String> TEXT_EXT = new HashSet<>(Arrays.asList(
            "json", "json5", "jsonc", "toml", "properties", "txt", "cfg", "conf", "config", "yml", "yaml",
            "ini", "mcmeta", "snbt", "log", "csv", "md", "lang", "js", "zs", "sh", "xml", "list"));
    private static final long MAX_TEXT = 1024 * 1024;
    private static final long MAX_UPLOAD = 2L * 1024 * 1024 * 1024;

    private final Host host;
    private String worldInfoCache;
    private long worldInfoAt;

    public FileApi(Host host) {
        this.host = host;
    }

    public boolean handles(String path) {
        return path.startsWith("/api/files/") || path.equals("/api/world/info");
    }

    public void handle(String path, MiniHttp.Req req, MiniHttp.Res res, boolean post) throws Exception {
        switch (path) {
            case "/api/world/info": worldInfo(req, res); return;
            case "/api/files/list": list(req, res); return;
            case "/api/files/download": download(req, res); return;
            case "/api/files/read": read(req, res); return;
            case "/api/files/write": mustPost(post); write(req, res); return;
            case "/api/files/upload": mustPost(post); upload(req, res); return;
            case "/api/files/delete": mustPost(post); delete(req, res); return;
            case "/api/files/mkdir": mustPost(post); mkdir(req, res); return;
            case "/api/files/rename": mustPost(post); rename(req, res); return;
            default: res.error(404, "Endpoint tidak ada");
        }
    }

    private static void mustPost(boolean post) {
        if (!post) throw new IllegalArgumentException("Pakai POST");
    }

    // ------------------------------------------------------------ paths

    private File root() throws IOException {
        return host.serverDir.getCanonicalFile();
    }

    /** Resolves a path relative to the server folder; anything outside it (.., symlinks) is refused. */
    File resolve(String rel) throws IOException {
        if (rel == null) rel = "";
        rel = rel.replace('\\', '/').trim();
        while (rel.startsWith("/")) rel = rel.substring(1);
        if (rel.indexOf('\0') >= 0) throw new IllegalArgumentException("Path tidak valid");
        File root = root();
        File c = (rel.isEmpty() ? root : new File(root, rel)).getCanonicalFile();
        if (!c.equals(root) && !c.getPath().startsWith(root.getPath() + File.separator)) {
            throw new IllegalArgumentException("Path di luar folder server");
        }
        return c;
    }

    private String rel(File f) throws IOException {
        String r = root().getPath();
        String p = f.getCanonicalPath();
        if (p.equals(r)) return "";
        return p.substring(r.length() + 1).replace(File.separatorChar, '/');
    }

    private boolean inWorld(File f) throws IOException {
        String p = f.getCanonicalPath();
        for (File d : host.worldDirs()) {
            String w = d.getCanonicalPath();
            if (p.equals(w) || p.startsWith(w + File.separator) || w.startsWith(p + File.separator)) return true;
        }
        return false;
    }

    private void requireStoppedFor(File f) throws IOException {
        if (host.mc.alive() && inWorld(f)) throw new IllegalStateException("Matikan server dulu untuk mengubah isi world");
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.US);
    }

    static boolean isText(String name) {
        return TEXT_EXT.contains(ext(name)) || name.equalsIgnoreCase("eula.txt");
    }

    // ------------------------------------------------------------ endpoints

    private void list(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File dir = resolve(req.param("path", ""));
        if (!dir.isDirectory()) throw new IllegalArgumentException("Folder tidak ditemukan");
        File[] kids = dir.listFiles();
        List<File> list = kids == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(kids));
        list.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            if (i > 0) arr.append(',');
            Json j = Json.obj().put("name", f.getName()).put("dir", f.isDirectory()).put("time", f.lastModified());
            if (f.isDirectory()) {
                String[] n = f.list();
                j.put("items", n == null ? 0 : n.length);
            } else {
                j.put("size", f.length()).put("text", isText(f.getName()));
            }
            arr.append(j.toString());
        }
        arr.append(']');
        String r = rel(dir);
        res.json(200, Json.obj()
                .put("path", r)
                .put("inWorld", inWorld(dir) && !dir.equals(root()))
                .put("running", host.mc.alive())
                .put("world", host.worldDir().getName())
                .raw("entries", arr.toString())
                .toString());
    }

    private void download(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = resolve(req.param("path", ""));
        if (!f.exists()) throw new IllegalArgumentException("File tidak ditemukan");
        if (f.isFile()) {
            res.file(f, "application/octet-stream", f.getName());
            return;
        }
        // Folder: zip it to a temp file first (consistent snapshot, known length).
        File tmpDir = new File(host.base, "tmp");
        tmpDir.mkdirs();
        File zip = new File(tmpDir, "dl-" + System.nanoTime() + ".zip");
        String name = f.equals(root()) ? "server" : f.getName();
        try {
            IO.zipDir(f, zip, name);
            res.file(zip, "application/zip", name + ".zip");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
    }

    private void read(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = resolve(req.param("path", ""));
        if (!f.isFile()) throw new IllegalArgumentException("File tidak ditemukan");
        if (f.length() > MAX_TEXT) throw new IllegalArgumentException("File terlalu besar untuk dibuka di editor (maks 1 MB)");
        byte[] b;
        try (InputStream in = new java.io.FileInputStream(f)) {
            b = IO.readAll(in, (int) MAX_TEXT + 1);
        }
        if (!isText(f.getName())) {
            for (int i = 0; i < Math.min(b.length, 8192); i++) {
                if (b[i] == 0) throw new IllegalArgumentException("Ini file biner, tidak bisa dibuka sebagai teks. Pakai Unduh.");
            }
        }
        res.send(200, "text/plain; charset=utf-8", b);
    }

    private void write(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = resolve(req.param("path", ""));
        if (f.isDirectory()) throw new IllegalArgumentException("Itu folder");
        if (!isText(f.getName())) throw new IllegalArgumentException("Cuma file teks/config yang bisa diedit");
        if (f.getParentFile() == null || !f.getParentFile().isDirectory()) throw new IllegalArgumentException("Folder tujuan tidak ada");
        if (req.contentLength() > MAX_TEXT) throw new IllegalArgumentException("Isi terlalu besar (maks 1 MB)");
        requireStoppedFor(f);
        byte[] body = req.bodyText((int) MAX_TEXT + 1).getBytes(StandardCharsets.UTF_8);
        File tmp = new File(f.getParentFile(), ".edit-" + System.nanoTime());
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(body);
        }
        if (f.exists() && !f.delete()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Gagal menimpa file");
        }
        if (!tmp.renameTo(f)) throw new IOException("Gagal menyimpan");
        host.mcLog.panel("File disimpan: " + rel(f));
        res.ok();
    }

    private void upload(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File dir = resolve(req.param("path", ""));
        String name = req.param("name", "");
        if (!dir.isDirectory()) throw new IllegalArgumentException("Folder tujuan tidak ada");
        if (!IO.safeName(name)) throw new IllegalArgumentException("Nama file tidak valid");
        if (req.contentLength() < 0) throw new IllegalArgumentException("Ukuran file tidak diketahui");
        if (req.contentLength() > MAX_UPLOAD) throw new IllegalArgumentException("File terlalu besar");
        File dest = resolve(rel(dir) + "/" + name);
        requireStoppedFor(dest);
        if (dest.isDirectory()) throw new IllegalArgumentException("Sudah ada folder dengan nama itu");
        File tmp = new File(dir, ".upload-" + System.nanoTime());
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
        host.mcLog.panel("Upload: " + rel(dest));
        res.ok();
    }

    private void delete(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = resolve(req.param("path", ""));
        if (f.equals(root())) throw new IllegalArgumentException("Folder server tidak bisa dihapus");
        if (!f.exists()) throw new IllegalArgumentException("File tidak ditemukan");
        requireStoppedFor(f);
        String r = rel(f);
        IO.deleteRecursive(f);
        if (f.exists()) throw new IOException("Sebagian file gagal dihapus");
        host.mcLog.panel("Dihapus: " + r);
        worldInfoCache = null;
        res.ok();
    }

    private void mkdir(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File dir = resolve(req.param("path", ""));
        String name = req.param("name", "").trim();
        if (!dir.isDirectory()) throw new IllegalArgumentException("Folder tidak ditemukan");
        if (!IO.safeName(name)) throw new IllegalArgumentException("Nama folder tidak valid");
        File f = resolve(rel(dir) + "/" + name);
        if (f.exists()) throw new IllegalArgumentException("Sudah ada");
        requireStoppedFor(f);
        if (!f.mkdir()) throw new IOException("Gagal membuat folder");
        res.ok();
    }

    private void rename(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        File f = resolve(req.param("path", ""));
        String to = req.param("to", "").trim();
        if (f.equals(root()) || !f.exists()) throw new IllegalArgumentException("File tidak ditemukan");
        if (!IO.safeName(to)) throw new IllegalArgumentException("Nama baru tidak valid");
        File dest = resolve(rel(f.getParentFile()) + "/" + to);
        if (dest.exists()) throw new IllegalArgumentException("Nama itu sudah dipakai");
        requireStoppedFor(f);
        if (!f.renameTo(dest)) throw new IOException("Gagal mengganti nama");
        res.ok();
    }

    private void worldInfo(MiniHttp.Req req, MiniHttp.Res res) throws IOException {
        long now = System.currentTimeMillis();
        if (worldInfoCache == null || now - worldInfoAt > 15_000 || "1".equals(req.param("force"))) {
            File w = host.worldDir(), netherDir = host.dimDir("nether"), endDir = host.dimDir("end");
            long nether = IO.dirSize(netherDir);
            long end = IO.dirSize(endDir);
            long total = 0;
            for (File d : host.worldDirs()) total += IO.dirSize(d);
            String addons = Software.addonDir(host.type());
            String root = host.serverDir.getPath() + File.separator;
            worldInfoCache = Json.obj()
                    .put("name", w.getName())
                    .put("exists", w.exists())
                    .put("size", total)
                    .put("overworld", Math.max(0, total - nether - end))
                    .put("nether", nether)
                    .put("netherExists", netherDir.exists())
                    .put("netherPath", netherDir.getPath().substring(root.length()).replace(File.separatorChar, '/'))
                    .put("end", end)
                    .put("endExists", endDir.exists())
                    .put("endPath", endDir.getPath().substring(root.length()).replace(File.separatorChar, '/'))
                    .put("addonDir", addons == null ? "" : addons)
                    .put("mods", addons == null ? 0 : IO.dirSize(new File(host.serverDir, addons)))
                    .put("config", IO.dirSize(new File(host.serverDir, "config")))
                    .put("backups", IO.dirSize(host.backupDir))
                    .toString();
            worldInfoAt = now;
        }
        res.json(200, worldInfoCache);
    }

    public void invalidate() {
        worldInfoCache = null;
    }
}
