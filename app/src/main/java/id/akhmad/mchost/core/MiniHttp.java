package id.akhmad.mchost.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal threaded HTTP/1.1 server (Connection: close). No external dependencies. */
public class MiniHttp {
    public interface Handler {
        void handle(Req req, Res res) throws Exception;
    }

    public static final class Req {
        public String method, path, query;
        public final Map<String, String> headers = new HashMap<>();
        public final Map<String, String> params = new HashMap<>();
        public InetAddress remote;
        InputStream body;
        long contentLength;
        boolean formParsed;

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }

        public String param(String name) {
            parseForm();
            return params.get(name);
        }

        public String param(String name, String def) {
            String v = param(name);
            return v == null ? def : v;
        }

        public boolean isLocal() {
            return remote != null && remote.isLoopbackAddress();
        }

        public String cookie(String name) {
            String c = header("cookie");
            if (c == null) return null;
            for (String part : c.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
            return null;
        }

        /** Raw body stream, limited to Content-Length. */
        public InputStream body() {
            return body;
        }

        public long contentLength() {
            return contentLength;
        }

        public String bodyText(int max) throws IOException {
            return new String(IO.readAll(body, max), StandardCharsets.UTF_8);
        }

        private void parseForm() {
            if (formParsed) return;
            formParsed = true;
            String ct = header("content-type");
            if ("POST".equals(method) && ct != null && ct.startsWith("application/x-www-form-urlencoded")) {
                try {
                    parseQuery(bodyText(1024 * 1024), params);
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static final class Res {
        private final OutputStream out;
        private boolean sent;
        private final Map<String, String> extra = new HashMap<>();

        Res(OutputStream out) {
            this.out = out;
        }

        public boolean sent() {
            return sent;
        }

        public void header(String k, String v) {
            extra.put(k, v);
        }

        private void head(int code, String type, long len) throws IOException {
            if (sent) throw new IllegalStateException("response already sent");
            sent = true;
            StringBuilder h = new StringBuilder();
            h.append("HTTP/1.1 ").append(code).append(' ').append(reason(code)).append("\r\n");
            h.append("Content-Type: ").append(type).append("\r\n");
            if (len >= 0) h.append("Content-Length: ").append(len).append("\r\n");
            h.append("Connection: close\r\n");
            h.append("Cache-Control: no-store\r\n");
            h.append("X-Content-Type-Options: nosniff\r\n");
            for (Map.Entry<String, String> e : extra.entrySet()) {
                h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            }
            h.append("\r\n");
            out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        }

        public void send(int code, String type, byte[] body) throws IOException {
            head(code, type, body.length);
            out.write(body);
            out.flush();
        }

        public void text(int code, String s) throws IOException {
            send(code, "text/plain; charset=utf-8", s.getBytes(StandardCharsets.UTF_8));
        }

        public void json(int code, String json) throws IOException {
            send(code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
        }

        public void ok() throws IOException {
            json(200, "{\"ok\":true}");
        }

        public void error(int code, String msg) throws IOException {
            json(code, Json.obj().put("ok", false).put("error", msg).toString());
        }

        public void stream(int code, String type, InputStream in, long len) throws IOException {
            head(code, type, len);
            IO.copy(in, out);
            out.flush();
        }

        public void file(File f, String type, String downloadName) throws IOException {
            if (downloadName != null) header("Content-Disposition", "attachment; filename=\"" + downloadName.replace("\"", "") + "\"");
            try (InputStream in = new FileInputStream(f)) {
                stream(200, type, in, f.length());
            }
        }
    }

    private final Handler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "http");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket server;

    public MiniHttp(Handler handler) {
        this.handler = handler;
    }

    public synchronized void start(int port) throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port), 64);
        final ServerSocket ss = server;
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    Socket s = ss.accept();
                    pool.execute(() -> serve(s));
                } catch (IOException e) {
                    if (ss.isClosed()) break;
                }
            }
        }, "http-accept");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void stop() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void serve(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(120_000);
            InputStream in = new BufferedInputStream(sock.getInputStream(), 16384);
            OutputStream out = new BufferedOutputStream(sock.getOutputStream(), 65536);
            Req req = new Req();
            req.remote = sock.getInetAddress();
            String line = readLine(in);
            if (line == null || line.isEmpty()) return;
            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            req.method = parts[0].toUpperCase(Locale.US);
            String target = parts[1];
            int q = target.indexOf('?');
            req.path = decode(q >= 0 ? target.substring(0, q) : target);
            req.query = q >= 0 ? target.substring(q + 1) : "";
            parseQuery(req.query, req.params);
            int headerCount = 0;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (++headerCount > 100) return;
                int c = line.indexOf(':');
                if (c > 0) req.headers.put(line.substring(0, c).trim().toLowerCase(Locale.US), line.substring(c + 1).trim());
            }
            long len = 0;
            try {
                String cl = req.header("content-length");
                if (cl != null) len = Long.parseLong(cl.trim());
            } catch (NumberFormatException ignored) {
            }
            req.contentLength = len;
            req.body = new Limited(in, len);
            Res res = new Res(out);
            try {
                handler.handle(req, res);
                if (!res.sent()) res.error(404, "Not found");
            } catch (Exception e) {
                if (!res.sent()) {
                    int code = e instanceof IllegalStateException ? 409
                            : e instanceof IllegalArgumentException ? 400 : 500;
                    res.error(code, e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
            out.flush();
        } catch (Exception ignored) {
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) throw new IOException("line too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    static void parseQuery(String q, Map<String, String> into) {
        if (q == null || q.isEmpty()) return;
        for (String kv : q.split("&")) {
            if (kv.isEmpty()) continue;
            int e = kv.indexOf('=');
            String k = decode(e >= 0 ? kv.substring(0, e) : kv);
            String v = e >= 0 ? decode(kv.substring(e + 1)) : "";
            into.put(k, v);
        }
    }

    static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 302: return "Found";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            default: return code >= 500 ? "Server Error" : "Status";
        }
    }

    private static final class Limited extends FilterInputStream {
        private long left;

        Limited(InputStream in, long len) {
            super(in);
            left = len;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int c = super.read();
            if (c >= 0) left--;
            return c;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }

        @Override
        public void close() {
        }
    }
}
