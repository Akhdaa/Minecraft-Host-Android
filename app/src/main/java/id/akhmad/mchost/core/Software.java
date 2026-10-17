package id.akhmad.mchost.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server software: where each type is downloaded from, how it is installed and how it is launched. */
public final class Software {
    public static final List<String> TYPES = Arrays.asList("paper", "purpur", "fabric", "forge", "neoforge", "vanilla");
    private static final String VER = "[0-9A-Za-z.\\-_+]{1,40}";

    private Software() {
    }

    public static boolean valid(String type) {
        return TYPES.contains(type);
    }

    public static boolean plugins(String type) {
        return "paper".equals(type) || "purpur".equals(type);
    }

    /** Folder that holds add-ons for this type ("mods" or "plugins"), or null for vanilla. */
    public static String addonDir(String type) {
        return plugins(type) ? "plugins" : "vanilla".equals(type) ? null : "mods";
    }

    public static String label(String type) {
        switch (type) {
            case "paper": return "Paper";
            case "purpur": return "Purpur";
            case "fabric": return "Fabric";
            case "forge": return "Forge";
            case "neoforge": return "NeoForge";
            default: return "Vanilla";
        }
    }

    public static void checkVersion(String v, boolean optional) {
        if ((optional && v.isEmpty()) || v.matches(VER)) return;
        throw new IllegalArgumentException("Versi tidak valid");
    }

    // ------------------------------------------------------------ java

    /** Java major Mojang ships for this version (version manifest), or a rule-of-thumb guess when offline. */
    public static int javaFor(String mc) {
        try {
            Matcher m = Pattern.compile("\"majorVersion\"\\s*:\\s*(\\d+)").matcher(mojangVersionJson(mc));
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
        }
        String[] p = mc.split("[.\\-]");
        try {
            int a = Integer.parseInt(p[0]), b = p.length > 1 ? Integer.parseInt(p[1]) : 0, c = p.length > 2 ? Integer.parseInt(p[2]) : 0;
            if (a >= 26) return 25;
            if (b > 20 || (b == 20 && c >= 5)) return 21;
            return b >= 17 ? 17 : 8;
        } catch (NumberFormatException e) {
            return 21;
        }
    }

    private static String mojangVersionJson(String mc) throws IOException {
        String manifest = text("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*\"" + Pattern.quote(mc) + "\"[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(manifest);
        if (!m.find()) throw new IOException("Versi Minecraft " + mc + " tidak ada di daftar Mojang");
        return text(m.group(1));
    }

    // ------------------------------------------------------------ install

    /** Result of an install: what goes after the JVM flags to start the server, and the resolved loader version. */
    public static final class Installed {
        public final String launch, loader;

        Installed(String launch, String loader) {
            this.launch = launch;
            this.loader = loader;
        }
    }

    /**
     * Downloads (and for Forge/NeoForge runs the installer of) {@code type} for Minecraft {@code mc} into
     * {@code dir}. {@code loader} empty = newest stable. {@code java} runs the Forge/NeoForge installer.
     */
    public static Installed install(String type, String mc, String loader, File dir, File java, Host host) throws Exception {
        LogBuffer log = host.mcLog;
        switch (type) {
            case "vanilla": {
                Matcher m = Pattern.compile("\"server\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(mojangVersionJson(mc));
                if (!m.find()) throw new IOException("Mojang tidak menyediakan server untuk " + mc);
                IO.download(m.group(1), new File(dir, "server.jar"), log);
                return new Installed("-jar server.jar", "");
            }
            case "paper": {
                String json = text("https://fill.papermc.io/v3/projects/paper/versions/" + mc + "/builds/latest");
                Matcher m = Pattern.compile("\"server:default\".*?\"url\"\\s*:\\s*\"([^\"]+)\"", Pattern.DOTALL).matcher(json);
                if (!m.find()) throw new IOException("Paper belum ada untuk " + mc);
                Matcher build = Pattern.compile("^\\{\\s*\"id\"\\s*:\\s*(\\d+)").matcher(json);
                IO.download(m.group(1), new File(dir, "server.jar"), log);
                return new Installed("-jar server.jar", build.find() ? build.group(1) : "");
            }
            case "purpur":
                IO.download("https://api.purpurmc.org/v2/purpur/" + mc + "/latest/download", new File(dir, "server.jar"), log);
                return new Installed("-jar server.jar", "");
            case "fabric": {
                if (loader.isEmpty()) loader = first(text("https://meta.fabricmc.net/v2/versions/loader/" + mc),
                        "\"loader\"\\s*:\\s*\\{[^}]*?\"version\"\\s*:\\s*\"([^\"]+)\"", "Fabric belum ada untuk " + mc);
                String installer = first(text("https://meta.fabricmc.net/v2/versions/installer"),
                        "\"version\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"stable\"\\s*:\\s*true", "Installer Fabric tidak ditemukan");
                checkVersion(loader, false);
                checkVersion(installer, false);
                IO.download("https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/" + installer + "/server/jar",
                        new File(dir, McServer.LAUNCHER), log);
                return new Installed("-jar " + McServer.LAUNCHER, loader);
            }
            case "forge": {
                if (loader.isEmpty()) loader = forgeVersion(mc);
                checkVersion(loader, false);
                String v = mc + "-" + loader;
                runInstaller("https://maven.minecraftforge.net/net/minecraftforge/forge/" + v + "/forge-" + v + "-installer.jar", dir, java, host);
                return new Installed(forgeLaunch(dir), loader);
            }
            case "neoforge": {
                if (loader.isEmpty()) loader = neoforgeVersion(mc);
                checkVersion(loader, false);
                runInstaller("https://maven.neoforged.net/releases/net/neoforged/neoforge/" + loader + "/neoforge-" + loader + "-installer.jar", dir, java, host);
                return new Installed(forgeLaunch(dir), loader);
            }
            default:
                throw new IllegalArgumentException("Jenis server tidak dikenal");
        }
    }

    private static String forgeVersion(String mc) throws IOException {
        String promos = text("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
        for (String kind : new String[]{"recommended", "latest"}) {
            Matcher m = Pattern.compile("\"" + Pattern.quote(mc + "-" + kind) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(promos);
            if (m.find()) return m.group(1);
        }
        throw new IOException("Forge belum ada untuk " + mc);
    }

    /** Newest stable NeoForge build for {@code mc} (beta only when nothing stable exists yet). */
    static String neoforgeVersion(String mc) throws IOException {
        String meta = text("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
        String stable = null, any = null;
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(meta);
        while (m.find()) {
            String v = m.group(1);
            if (!mc.equals(neoforgeMc(v))) continue;
            any = v;
            if (!v.contains("beta") && !v.contains("alpha")) stable = v;
        }
        if (stable != null) return stable;
        if (any != null) return any;
        throw new IOException("NeoForge belum ada untuk " + mc);
    }

    /** NeoForge numbers builds after Minecraft: 21.1.x -> 1.21.1, 21.0.x -> 1.21, 26.1.2.x -> 26.1.2, 26.3.0.x -> 26.3. */
    static String neoforgeMc(String v) {
        String[] p = v.split("[.\\-]");
        if (p.length < 3) return "";
        try {
            int a = Integer.parseInt(p[0]);
            if (a >= 26) return a + "." + p[1] + ("0".equals(p[2]) ? "" : "." + p[2]);
            return "1." + a + ("0".equals(p[1]) ? "" : "." + p[1]);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static void runInstaller(String url, File dir, File java, Host host) throws Exception {
        File jar = new File(dir, "installer.jar");
        IO.download(url, jar, host.mcLog);
        File tmp = new File(host.base, "tmp");
        tmp.mkdirs();
        host.mcLog.panel("Menjalankan installer (download library, bisa beberapa menit)...");
        List<String> cmd = new ArrayList<>(Arrays.asList(java.getAbsolutePath(), "-Djava.io.tmpdir=" + tmp.getAbsolutePath(),
                "-jar", jar.getName(), "--installServer"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true);
        pb.environment().putAll(host.platform.childEnv(java));
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) host.mcLog.add(line);
        }
        int code = p.waitFor();
        //noinspection ResultOfMethodCallIgnored
        jar.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dir, "installer.jar.log").delete();
        if (code != 0) throw new IOException("Installer gagal (exit " + code + "), cek log di atas");
    }

    /** Modern Forge/NeoForge start through an args file under libraries/, older Forge through a runnable jar. */
    static String forgeLaunch(File dir) throws IOException {
        File best = null;
        for (String base : new String[]{"libraries/net/minecraftforge/forge", "libraries/net/neoforged/neoforge", "libraries/net/neoforged/forge"}) {
            File[] vers = new File(dir, base).listFiles();
            if (vers == null) continue;
            for (File v : vers) {
                File args = new File(v, "unix_args.txt");
                if (args.isFile() && (best == null || args.lastModified() > best.lastModified())) best = args;
            }
        }
        if (best != null) {
            String root = dir.getCanonicalPath();
            return "@" + best.getCanonicalPath().substring(root.length() + 1).replace(File.separatorChar, '/');
        }
        File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar") && (n.startsWith("forge-") || n.startsWith("neoforge-")) && !n.contains("installer"));
        if (jars != null && jars.length > 0) return "-jar " + jars[0].getName();
        throw new IOException("Installer selesai tapi file start server tidak ditemukan");
    }

    private static String first(String text, String regex, String error) throws IOException {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) throw new IOException(error);
        return m.group(1);
    }

    private static String text(String url) throws IOException {
        return new String(IO.fetch(url, 16 * 1024 * 1024), StandardCharsets.UTF_8);
    }
}
