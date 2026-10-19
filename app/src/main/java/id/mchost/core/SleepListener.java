package id.mchost.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Stands in for a sleeping server on its port: server-list pings get a "sleeping" MOTD, and a join attempt is
 * turned away with a short message while the real server starts. Speaks just enough of the Minecraft protocol
 * (handshake, status, ping, login disconnect), which is the same for every version MC Host supports.
 */
final class SleepListener {
    private final ServerSocket socket;
    private final String motd, kick;
    private final int maxPlayers;
    private final Runnable onJoin;
    private volatile boolean closed;

    SleepListener(int port, String motd, String kick, int maxPlayers, Runnable onJoin) throws IOException {
        this.motd = motd;
        this.kick = kick;
        this.maxPlayers = maxPlayers;
        this.onJoin = onJoin;
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        Thread t = new Thread(this::loop, "sleep-listener");
        t.setDaemon(true);
        t.start();
    }

    void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private void loop() {
        while (!closed) {
            try (Socket s = socket.accept()) {
                s.setSoTimeout(5000);
                if (handle(s)) {
                    close();
                    onJoin.run();
                    return;
                }
            } catch (IOException e) {
                if (closed) return;
            }
        }
    }

    /** Answers one connection; true when it was a join attempt (time to wake the server). */
    private boolean handle(Socket s) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        OutputStream out = s.getOutputStream();
        in.mark(1);
        int first = in.read();
        if (first < 0 || first == 0xFE) return false; // nothing, or the pre-1.7 legacy ping
        in.reset();
        readVarInt(in); // packet length
        if (readVarInt(in) != 0) return false; // not a handshake
        int protocol = readVarInt(in);
        readString(in); // address the client typed
        in.readUnsignedShort();
        int next = readVarInt(in);
        if (next == 1) {
            readVarInt(in); // status request: length
            readVarInt(in); // and id
            String json = "{\"version\":{\"name\":\"Sleeping\",\"protocol\":" + protocol + "},\"players\":{\"max\":" + maxPlayers
                    + ",\"online\":0},\"description\":{\"text\":" + Json.str(motd) + "}}";
            send(out, 0, string(json));
            try {
                readVarInt(in);
                if (readVarInt(in) == 1) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    long v = in.readLong();
                    for (int i = 7; i >= 0; i--) b.write((int) (v >>> (i * 8)));
                    send(out, 1, b.toByteArray());
                }
            } catch (IOException ignored) {
                // some clients close right after the status response
            }
            return false;
        }
        // login (2) or transfer (3): say why, then wake up
        send(out, 0, string("{\"text\":" + Json.str(kick) + "}"));
        return true;
    }

    private static void send(OutputStream out, int id, byte[] data) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeVarInt(body, id);
        body.write(data);
        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        writeVarInt(pkt, body.size());
        body.writeTo(pkt);
        out.write(pkt.toByteArray());
        out.flush();
    }

    private static byte[] string(String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        writeVarInt(o, b.length);
        o.write(b);
        return o.toByteArray();
    }

    private static String readString(DataInputStream in) throws IOException {
        int n = readVarInt(in);
        if (n < 0 || n > 32767) throw new IOException("bad string");
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }

    private static void writeVarInt(ByteArrayOutputStream o, int v) {
        while ((v & ~0x7F) != 0) {
            o.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        o.write(v);
    }
}
