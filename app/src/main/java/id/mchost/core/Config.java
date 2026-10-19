package id.mchost.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Properties;

/** Persistent panel settings (panel.properties). */
public class Config {
    public static final String RAM_MB = "ram_mb";
    public static final String JVM_ARGS = "jvm_args";
    public static final String AUTO_RESTART = "auto_restart";
    public static final String AUTO_START = "auto_start";
    public static final String BOOT_START = "boot_start";
    public static final String PIN = "pin";
    public static final String EULA = "eula";
    public static final String MC_VERSION = "mc_version";
    public static final String LOADER = "loader";
    public static final String INSTALLER = "installer";
    public static final String PLAYIT_SECRET = "playit_secret";
    public static final String PLAYIT_AUTOSTART = "playit_autostart";
    public static final String PLAYIT_WITH_SERVER = "playit_with_server";
    public static final String PORT = "port";
    public static final String CUSTOM_DOMAIN = "custom_domain";
    public static final String CF_TOKEN = "cf_token";
    public static final String CF_AUTOSYNC = "cf_autosync";
    public static final String AUTO_BACKUP_HOURS = "auto_backup_hours";
    public static final String BACKUP_KEEP = "backup_keep";
    public static final String ACTIVE = "active_server";
    public static final String LANG = "lang";
    public static final String BACKUP_FULL = "backup_full";
    public static final String NOTIFY_SERVER = "notify_server";
    public static final String NOTIFY_PLAYERS = "notify_players";
    public static final String NOTIFY_HEAT = "notify_heat";
    public static final String PORT_FORWARD = "port_forward";
    public static final String CF_ENABLED = "cf_enabled";
    // per-server keys (servers/<id>/mchost.properties); RAM_MB, JVM_ARGS, MC_VERSION, LOADER, INSTALLER live there too
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String LAUNCH = "launch";
    public static final String JAVA = "java";

    private final File file;
    private final Properties p = new Properties();

    /** Panel settings (panel.properties) with defaults and a random PIN. */
    public Config(File file) {
        this(file, true);
    }

    /** {@code panel=false}: a plain properties file without the panel defaults (per-server settings). */
    public Config(File file, boolean panel) {
        this.file = file;
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                p.load(in);
            } catch (Exception ignored) {
            }
        }
        if (!panel) return;
        def(RAM_MB, "3072");
        def(JVM_ARGS, "");
        def(AUTO_RESTART, "true");
        def(LANG, "en");
        def(BACKUP_FULL, "false");
        def(NOTIFY_SERVER, "false");
        def(NOTIFY_PLAYERS, "false");
        def(NOTIFY_HEAT, "false");
        def(AUTO_START, "false");
        def(BOOT_START, "false");
        def(EULA, "false");
        def(MC_VERSION, "1.20.1");
        def(LOADER, "");
        def(INSTALLER, "");
        def(PLAYIT_SECRET, "");
        def(PLAYIT_AUTOSTART, "false");
        def(PLAYIT_WITH_SERVER, "true");
        def(PORT, "8080");
        def(CUSTOM_DOMAIN, "");
        def(CF_TOKEN, "");
        def(CF_AUTOSYNC, "true");
        def(AUTO_BACKUP_HOURS, "0");
        def(BACKUP_KEEP, "5");
        if (get(PIN, "").isEmpty()) {
            SecureRandom r = new SecureRandom();
            p.setProperty(PIN, String.valueOf(100000 + r.nextInt(900000)));
        }
        save();
    }

    private void def(String k, String v) {
        if (p.getProperty(k) == null) p.setProperty(k, v);
    }

    public synchronized String get(String k, String def) {
        String v = p.getProperty(k);
        return v == null ? def : v;
    }

    public synchronized int getInt(String k, int def) {
        try {
            return Integer.parseInt(get(k, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized boolean getBool(String k) {
        return "true".equalsIgnoreCase(get(k, "false"));
    }

    public synchronized void set(String k, String v) {
        p.setProperty(k, v == null ? "" : v);
    }

    public synchronized void save() {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            p.store(out, "MC Host panel settings");
        } catch (Exception ignored) {
        }
    }
}
