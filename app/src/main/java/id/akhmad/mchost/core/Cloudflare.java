package id.akhmad.mchost.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain helper: works out the SRV record a Minecraft domain needs for a playit tunnel,
 * checks what public DNS currently says, and (with an API token) writes it to Cloudflare.
 * Responses are scanned with regexes on purpose, so the core stays dependency free.
 */
public final class Cloudflare {
    private static final String DOH = System.getProperty("mchost.dohApi", "https://dns.google") + "/resolve";
    private static final String CF = System.getProperty("mchost.cfApi", "https://api.cloudflare.com") + "/client/v4";
    private static final Pattern SRV_DATA = Pattern.compile("\"data\"\\s*:\\s*\"(\\d+) (\\d+) (\\d+) ([^\"\\s]+?)\\.?\"");
    private static final Pattern HOST = Pattern.compile("[a-z0-9]([a-z0-9\\-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]*[a-z0-9])?)+");

    private Cloudflare() {
    }

    public static final class Srv {
        public final int priority, weight, port;
        public final String target;

        Srv(int priority, int weight, int port, String target) {
            this.priority = priority;
            this.weight = weight;
            this.port = port;
            this.target = target.toLowerCase();
        }

        public boolean sameTarget(Srv o) {
            return o != null && o.port == port && o.target.equals(target);
        }

        public String json() {
            return Json.obj().put("priority", priority).put("weight", weight).put("port", port).put("target", target).toString();
        }
    }

    public static String srvName(String domain) {
        return "_minecraft._tcp." + domain;
    }

    /** Validates a bare host name like mc.example.com. */
    public static String cleanHost(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase().replaceAll("^[a-z]+://", "").replaceAll("[/:].*$", "").replaceAll("\\.$", "");
        if (s.isEmpty()) return "";
        if (s.length() > 253 || !HOST.matcher(s).matches()) throw new IllegalArgumentException("Nama domain tidak valid: " + s);
        return s;
    }

    /** First SRV answer for _minecraft._tcp.host via DNS-over-HTTPS, or null. */
    public static Srv lookup(String host) throws IOException {
        String url = DOH + "?type=SRV&name=" + URLEncoder.encode(srvName(host), "UTF-8") + "&nocache=" + System.nanoTime();
        String body = new String(IO.fetch(url, 256 * 1024), StandardCharsets.UTF_8);
        Matcher m = SRV_DATA.matcher(body);
        if (!m.find()) return null;
        return new Srv(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), m.group(4));
    }

    /**
     * Turns a playit display address into the real host+port a SRV record must point at.
     * "x.ply.gg:41234" is used as is; "x.joinmc.link" is itself served through SRV, so we follow it.
     */
    public static Srv targetFor(String displayAddress) throws IOException {
        String a = displayAddress.trim().toLowerCase();
        int c = a.lastIndexOf(':');
        if (c > 0) {
            String host = cleanHost(a.substring(0, c));
            int port = Integer.parseInt(a.substring(c + 1).replaceAll("[^0-9]", ""));
            return new Srv(0, 5, port, host);
        }
        String host = cleanHost(a);
        Srv s = lookup(host);
        if (s != null) return new Srv(0, 5, s.port, s.target);
        return new Srv(0, 5, 25565, host);
    }

    // ------------------------------------------------------------ Cloudflare API

    private static String call(String method, String path, String token, String body) throws IOException {
        HttpURLConnection c = IO.open(CF + path);
        try {
            c.setRequestMethod(method);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Content-Type", "application/json");
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(IO.readAll(in, 1024 * 1024), StandardCharsets.UTF_8);
            if (!Pattern.compile("\"success\"\\s*:\\s*true").matcher(text).find()) {
                Matcher m = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
                String msg = m.find() ? m.group(1) : ("HTTP " + code);
                if (code == 401 || code == 403) msg += " (cek izin token: Zone > DNS > Edit untuk domain ini)";
                throw new IOException("Cloudflare: " + msg);
            }
            return text;
        } finally {
            c.disconnect();
        }
    }

    private static String firstId(String json) {
        Matcher m = Pattern.compile("\"result\"\\s*:\\s*\\[\\s*\\{[^\\[]*?\"id\"\\s*:\\s*\"([0-9a-f]{32})\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Finds the Cloudflare zone holding this host by trying its parent names, longest first. */
    private static String[] zoneFor(String host, String token) throws IOException {
        String[] labels = host.split("\\.");
        for (int i = 0; i <= labels.length - 2; i++) {
            StringBuilder name = new StringBuilder();
            for (int j = i; j < labels.length; j++) {
                if (name.length() > 0) name.append('.');
                name.append(labels[j]);
            }
            String id = firstId(call("GET", "/zones?name=" + URLEncoder.encode(name.toString(), "UTF-8"), token, null));
            if (id != null) return new String[]{id, name.toString()};
        }
        throw new IOException("Domain " + host + " tidak ditemukan di akun Cloudflare token ini");
    }

    /** Creates or updates _minecraft._tcp.host -> target. Returns a short human summary. */
    public static String upsert(String host, Srv target, String token) throws IOException {
        String[] zone = zoneFor(host, token);
        String name = srvName(host);
        String body = Json.obj()
                .put("type", "SRV")
                .put("name", name)
                .put("ttl", 60)
                .raw("data", Json.obj().put("priority", target.priority).put("weight", target.weight)
                        .put("port", target.port).put("target", target.target).toString())
                .put("comment", "MC Host (playit tunnel)")
                .toString();
        String list = call("GET", "/zones/" + zone[0] + "/dns_records?type=SRV&name=" + URLEncoder.encode(name, "UTF-8"), token, null);
        String id = firstId(list);
        if (id == null) {
            call("POST", "/zones/" + zone[0] + "/dns_records", token, body);
            return "Record SRV dibuat di zone " + zone[1];
        }
        call("PUT", "/zones/" + zone[0] + "/dns_records/" + id, token, body);
        return "Record SRV diperbarui di zone " + zone[1];
    }

    /** True when the token is valid (does not check zone permissions). */
    public static void verifyToken(String token) throws IOException {
        call("GET", "/user/tokens/verify", token, null);
    }
}
