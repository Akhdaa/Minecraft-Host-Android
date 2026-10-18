package id.mchost.core;

import java.util.Collection;

/** Tiny JSON writer (the panel never needs to parse JSON on the backend). */
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
}
