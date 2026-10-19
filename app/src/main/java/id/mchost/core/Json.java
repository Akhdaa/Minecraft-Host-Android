package id.mchost.core;

import java.util.Collection;

/** Tiny JSON writer, plus a minimal reader for Modrinth API replies and modpack indexes. */
public final class Json {
    private final StringBuilder sb = new StringBuilder();
    private boolean first = true;

    private Json(char open) {
        sb.append(open);
    }

    public static Json obj() {
        return new Json('{');
    }

    public static String str(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder(s.length() + 8).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c == ' ' || c == ' ') b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    public static String strArray(Collection<String> items) {
        StringBuilder b = new StringBuilder("[");
        boolean f = true;
        for (String s : items) {
            if (!f) b.append(',');
            b.append(str(s));
            f = false;
        }
        return b.append(']').toString();
    }

    private Json key(String k) {
        if (!first) sb.append(',');
        first = false;
        sb.append(str(k)).append(':');
        return this;
    }

    public Json put(String k, String v) {
        key(k).sb.append(str(v));
        return this;
    }

    public Json put(String k, long v) {
        key(k).sb.append(v);
        return this;
    }

    public Json put(String k, double v) {
        key(k).sb.append(v);
        return this;
    }

    public Json put(String k, boolean v) {
        key(k).sb.append(v);
        return this;
    }

    /** Insert an already-serialized JSON value. */
    public Json raw(String k, String json) {
        key(k).sb.append(json == null ? "null" : json);
        return this;
    }

    @Override
    public String toString() {
        return sb.toString() + '}';
    }

    // ---------------------------------------------------------------- reading (Modrinth API, modpack index)

    /** Minimal JSON reader: objects become Map, arrays List, numbers Double, plus String, Boolean and null. */
    public static Object parse(String s) {
        int[] at = {0};
        Object v = value(s, at);
        skip(s, at);
        if (at[0] != s.length()) throw new IllegalArgumentException("JSON tidak valid di posisi " + at[0]);
        return v;
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> asMap(Object o) {
        return o instanceof java.util.Map ? (java.util.Map<String, Object>) o : new java.util.HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<Object> asList(Object o) {
        return o instanceof java.util.List ? (java.util.List<Object>) o : new java.util.ArrayList<>();
    }

    public static String asStr(Object o) {
        return o instanceof String ? (String) o : "";
    }

    private static Object value(String s, int[] at) {
        skip(s, at);
        if (at[0] >= s.length()) throw new IllegalArgumentException("JSON terpotong");
        char c = s.charAt(at[0]);
        if (c == '{') {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            at[0]++;
            skip(s, at);
            if (peek(s, at) == '}') {
                at[0]++;
                return m;
            }
            while (true) {
                skip(s, at);
                String k = string(s, at);
                skip(s, at);
                expect(s, at, ':');
                m.put(k, value(s, at));
                skip(s, at);
                if (peek(s, at) == ',') {
                    at[0]++;
                    continue;
                }
                expect(s, at, '}');
                return m;
            }
        }
        if (c == '[') {
            java.util.List<Object> l = new java.util.ArrayList<>();
            at[0]++;
            skip(s, at);
            if (peek(s, at) == ']') {
                at[0]++;
                return l;
            }
            while (true) {
                l.add(value(s, at));
                skip(s, at);
                if (peek(s, at) == ',') {
                    at[0]++;
                    continue;
                }
                expect(s, at, ']');
                return l;
            }
        }
        if (c == '"') return string(s, at);
        if (s.startsWith("true", at[0])) {
            at[0] += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", at[0])) {
            at[0] += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", at[0])) {
            at[0] += 4;
            return null;
        }
        int st = at[0];
        while (at[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(at[0])) >= 0) at[0]++;
        if (st == at[0]) throw new IllegalArgumentException("JSON tidak valid di posisi " + st);
        return Double.parseDouble(s.substring(st, at[0]));
    }

    private static String string(String s, int[] at) {
        expect(s, at, '"');
        StringBuilder b = new StringBuilder();
        while (true) {
            if (at[0] >= s.length()) throw new IllegalArgumentException("JSON terpotong");
            char c = s.charAt(at[0]++);
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = s.charAt(at[0]++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    b.append((char) Integer.parseInt(s.substring(at[0], at[0] + 4), 16));
                    at[0] += 4;
                    break;
                default: b.append(e); // \" \\ \/
            }
        }
    }

    private static char peek(String s, int[] at) {
        return at[0] < s.length() ? s.charAt(at[0]) : '\0';
    }

    private static void skip(String s, int[] at) {
        while (at[0] < s.length() && Character.isWhitespace(s.charAt(at[0]))) at[0]++;
    }

    private static void expect(String s, int[] at, char c) {
        if (peek(s, at) != c) throw new IllegalArgumentException("JSON tidak valid: '" + c + "' di posisi " + at[0]);
        at[0]++;
    }
}
