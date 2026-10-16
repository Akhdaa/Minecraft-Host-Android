package id.akhmad.mchost.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** File, network, tar and zip helpers. Java 8 API only (Android minSdk 26). */
public final class IO {
    public static final String USER_AGENT = "akhmad/mchost/1.0 (Android Minecraft server panel)";

    private IO() {
    }

    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            total += n;
        }
        return total;
    }

    public static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
            if (bos.size() > max) throw new IOException("Response too large");
        }
        return bos.toByteArray();
    }

    public static String readText(File f) throws IOException {
        if (!f.exists()) return "";
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in, 8 * 1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    public static void writeText(File f, String s) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null && !isSymlink(f)) {
            for (File k : kids) deleteRecursive(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static boolean isSymlink(File f) {
        try {
            return !f.getCanonicalFile().equals(f.getAbsoluteFile());
        } catch (IOException e) {
            return false;
        }
    }

    /** Plain file name: no path separators, no parent refs, no leading dot. */
    public static boolean safeName(String name) {
        return name != null && !name.isEmpty() && name.length() < 200
                && !name.contains("/") && !name.contains("\\") && !name.startsWith(".")
                && !name.contains("\u0000");
    }

    public static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", USER_AGENT);
        return c;
    }

    public static byte[] fetch(String url, int max) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " from " + url);
            try (InputStream in = c.getInputStream()) {
                return readAll(in, max);
            }
        } finally {
            c.disconnect();
        }
    }

    /** POSTs a JSON body and returns the response body (also for 4xx, so API errors are readable). */
    public static byte[] postJson(String url, String json, String authorization, int max) throws IOException {
        HttpURLConnection c = open(url);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            if (authorization != null) c.setRequestProperty("Authorization", authorization);
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code + " dari " + url);
            try (InputStream body = in) {
                return readAll(body, max);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Downloads to a temp file and renames on success. */
    public static long download(String url, File dest, LogBuffer log) throws IOException {
        HttpURLConnection c = open(url);
        File tmp = new File(dest.getPath() + ".part");
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " dari " + url);
            long len = c.getContentLengthLong();
            dest.getParentFile().mkdirs();
            long total = 0, lastReport = 0;
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    total += n;
                    if (log != null && len > 2_000_000 && total - lastReport > len / 5) {
                        lastReport = total;
                        log.panel(String.format(Locale.US, "Download %s: %d%%", dest.getName(), total * 100 / len));
                    }
                }
            }
            if (dest.exists() && !dest.delete()) throw new IOException("Gagal menimpa " + dest);
            if (!tmp.renameTo(dest)) throw new IOException("Gagal rename " + tmp);
            return total;
        } finally {
            c.disconnect();
            if (tmp.exists()) //noinspection ResultOfMethodCallIgnored
                tmp.delete();
        }
    }

    // ---------------------------------------------------------------- tar.gz

    /** Extracts a (GNU/ustar) .tar.gz stream, keeping executable bits. Returns file count. */
    public static int extractTarGz(InputStream raw, File destDir) throws IOException {
        String destCanon = destDir.getCanonicalPath() + File.separator;
        DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 65536), 65536));
        byte[] header = new byte[512];
        String longName = null;
        int count = 0;
        while (true) {
            try {
                in.readFully(header);
            } catch (EOFException e) {
                break;
            }
            if (isZeroBlock(header)) break;
            String name = cstr(header, 0, 100);
            String prefix = cstr(header, 345, 155);
            int mode = (int) octal(header, 100, 8);
            long size = octal(header, 124, 12);
            char type = (char) header[156];
            if (longName != null) {
                name = longName;
                longName = null;
            } else if (!prefix.isEmpty() && header[257] == 'u') {
                name = prefix + "/" + name;
            }
            long padded = (size + 511) / 512 * 512;
            if (type == 'L') { // GNU long name
                byte[] b = new byte[(int) padded];
                in.readFully(b);
                longName = cstr(b, 0, (int) size);
                continue;
            }
            if (type == 'x' || type == 'g' || type == '2' || type == '1' || type == 'K') {
                skipFully(in, padded); // pax headers / links: our CI archive dereferences links
                continue;
            }
            while (name.startsWith("./")) name = name.substring(2);
            if (name.isEmpty()) {
                skipFully(in, padded);
                continue;
            }
            File out = new File(destDir, name);
            if (!out.getCanonicalPath().startsWith(destCanon) && !out.getCanonicalPath().equals(destDir.getCanonicalPath())) {
                throw new IOException("Path di luar folder: " + name);
            }
            if (type == '5') {
                out.mkdirs();
                skipFully(in, padded);
                continue;
            }
            out.getParentFile().mkdirs();
            try (OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                long left = size;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) throw new EOFException("tar terpotong");
                    os.write(buf, 0, n);
                    left -= n;
                }
            }
            skipFully(in, padded - size);
            if ((mode & 0111) != 0) //noinspection ResultOfMethodCallIgnored
                out.setExecutable(true, false);
            count++;
        }
        return count;
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && end < b.length && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        if ((b[off] & 0x80) != 0) { // GNU base-256
            long v = b[off] & 0x7f;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (b[i] & 0xff);
            return v;
        }
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') {
                if (v != 0) break;
                continue;
            }
            if (c < '0' || c > '7') break;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    private static void skipFully(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) throw new EOFException();
            n -= r;
        }
    }

    // ---------------------------------------------------------------- zip

    public static void zipDir(File dir, File zipFile, String rootName) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.setLevel(5);
            addToZip(zos, dir, rootName);
        }
    }

    private static void addToZip(ZipOutputStream zos, File f, String path) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            zos.putNextEntry(new ZipEntry(path + "/"));
            zos.closeEntry();
            if (kids != null) for (File k : kids) addToZip(zos, k, path + "/" + k.getName());
            return;
        }
        if (f.getName().equals("session.lock")) return;
        zos.putNextEntry(new ZipEntry(path));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, zos);
        }
        zos.closeEntry();
    }

    /**
     * Extracts a world zip into destDir. If every entry shares one top folder containing
     * level.dat, that folder is stripped, so both "world/level.dat" and "level.dat" zips work.
     */
    public static void unzipWorld(File zip, File destDir) throws IOException {
        String strip = detectWorldRoot(zip);
        String destCanon = destDir.getCanonicalPath() + File.separator;
        destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (strip != null) {
                    if (!name.startsWith(strip)) continue;
                    name = name.substring(strip.length());
                }
                if (name.isEmpty()) continue;
                File out = new File(destDir, name);
                if (!out.getCanonicalPath().startsWith(destCanon)) throw new IOException("Zip berbahaya: " + e.getName());
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    out.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        copy(zis, os);
                    }
                }
            }
        }
    }

    private static String detectWorldRoot(File zip) throws IOException {
        String levelDatParent = null;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                if (n.endsWith("level.dat")) {
                    String parent = n.substring(0, n.length() - "level.dat".length());
                    if (levelDatParent == null || parent.length() < levelDatParent.length()) levelDatParent = parent;
                }
            }
        }
        if (levelDatParent == null) throw new IOException("Zip ini bukan world Minecraft (level.dat tidak ada)");
        return levelDatParent.isEmpty() ? null : levelDatParent;
    }
}
