package id.mchost.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** A child process whose merged stdout/stderr goes into a LogBuffer. */
public abstract class ManagedProcess {
    protected final LogBuffer log;
    private Process proc;
    private Writer stdin;
    private long startedAt;
    private int pid = -1;
    private String pidHint;
    protected volatile boolean stopRequested;

    protected ManagedProcess(LogBuffer log) {
        this.log = log;
    }

    public synchronized boolean alive() {
        return proc != null && isAlive(proc);
    }

    private static boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    protected synchronized void launch(List<String> cmd, File dir, Map<String, String> env, String pidHint) throws IOException {
        if (alive()) throw new IOException("Proses sudah jalan");
        dir.mkdirs();
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true);
        if (env != null) pb.environment().putAll(env);
        stopRequested = false;
        final Process p = pb.start();
        proc = p;
        startedAt = System.currentTimeMillis();
        stdin = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8);
        this.pidHint = pidHint;
        pid = findPid(p, null);
        Thread t = new Thread(() -> pump(p), getClass().getSimpleName() + "-out");
        t.setDaemon(true);
        t.start();
    }

    private void pump(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                log.add(line);
                try {
                    onLine(line);
                } catch (RuntimeException ignored) {
                    // a parser choking on odd output (player chat included) must never stop draining the pipe
                }
            }
        } catch (IOException ignored) {
        }
        int code;
        try {
            code = p.waitFor();
        } catch (InterruptedException e) {
            code = -1;
        }
        synchronized (this) {
            if (proc == p) {
                proc = null;
                stdin = null;
                pid = -1;
            }
        }
        onExit(code, stopRequested);
    }

    protected abstract void onLine(String line);

    protected abstract void onExit(int code, boolean requested);

    public synchronized boolean send(String line) {
        if (stdin == null) return false;
        try {
            stdin.write(line);
            stdin.write('\n');
            stdin.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Forcefully kills the process. */
    public void kill() {
        Process p;
        synchronized (this) {
            p = proc;
            stopRequested = true;
        }
        if (p != null) p.destroyForcibly();
    }

    /** Waits until the process is gone or timeout elapses. Returns true if gone. */
    public boolean waitGone(long ms) {
        Process p;
        synchronized (this) {
            p = proc;
        }
        if (p == null) return true;
        try {
            return p.waitFor(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return !isAlive(p);
        }
    }

    public synchronized long uptimeSec() {
        return alive() ? (System.currentTimeMillis() - startedAt) / 1000 : 0;
    }

    /** OS process id of the running child, or -1 (stats are read through Platform with it). */
    public int pid() {
        synchronized (this) {
            if (pid <= 0 && proc != null) pid = findPid(proc, pidHint);
            return proc == null ? -1 : pid;
        }
    }

    private static int findPid(Process p, String hint) {
        try { // Java 9+ / Android 13+
            Method m = Process.class.getMethod("pid");
            return ((Long) m.invoke(p)).intValue();
        } catch (Throwable ignored) {
        }
        for (Class<?> c = p.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("pid");
                f.setAccessible(true);
                return f.getInt(p);
            } catch (Throwable ignored) {
            }
        }
        // Fallback: newest /proc entry of ours whose cmdline contains the hint.
        if (hint == null) return -1;
        int best = -1;
        File[] procs = new File("/proc").listFiles();
        if (procs == null) return -1;
        for (File d : procs) {
            String n = d.getName();
            if (!n.matches("\\d+")) continue;
            try {
                String cmd = IO.readText(new File(d, "cmdline"));
                if (cmd.contains(hint)) best = Math.max(best, Integer.parseInt(n));
            } catch (Exception ignored) {
            }
        }
        return best;
    }
}
