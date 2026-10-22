package id.mchost.desktop;

import id.mchost.core.Config;
import id.mchost.core.Host;
import id.mchost.core.IO;
import id.mchost.core.MiniHttp;
import id.mchost.core.PanelApi;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static id.mchost.core.Host.tr;

/** MCHost.exe: runs the host in the background, shows the panel in a WebView2 window and stays in the tray when closed. */
public final class DesktopMain {
    private final WindowsPlatform platform;
    private final Host host;
    private final MiniHttp http;
    private final int port;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private String portError;
    private Display display;
    private Shell shell;
    private Browser web; // null when WebView2 is missing: the panel then opens in the default browser
    private TrayItem trayItem;
    private ToolTip balloon;
    private boolean trayHintShown;

    public static void main(String[] args) {
        WindowsPlatform platform = new WindowsPlatform();
        if (showRunningCopy(platform.baseDir())) return; // that copy owns the server and shows its window
        // WebView2 keeps its profile next to the exe by default, which is read-only under Program Files
        System.setProperty("org.eclipse.swt.browser.EdgeDataDir", new File(platform.baseDir(), "webview").getPath());
        new DesktopMain(platform).run(!Arrays.asList(args).contains("--tray"));
    }

    private DesktopMain(WindowsPlatform platform) {
        this.platform = platform;
        host = new Host(platform);
        port = host.cfg.getInt(Config.PORT, 8080);
        http = new MiniHttp(new PanelApi(host));
        try {
            http.start(port);
        } catch (IOException e) {
            portError = tr("Port " + port + " is used by another program, so the panel can't open: ",
                    "Port " + port + " dipakai program lain, panel tidak bisa dibuka: ") + e.getMessage();
            host.mcLog.panel(portError);
        }
        host.init();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopHost, "shutdown-hook")); // Windows sign-out / shutdown
    }

    private void run(boolean showWindow) {
        Display.setAppName("MC Host");
        display = new Display();
        Image logo = new Image(display, DesktopMain.class.getResourceAsStream("/panel/img/logo.png"));
        shell = new Shell(display);
        shell.setText("MC Host");
        shell.setImage(logo);
        shell.setLayout(new FillLayout());
        shell.setSize(1280, 820);
        try {
            web = new Browser(shell, SWT.EDGE);
        } catch (SWTError noWebView2) {
            web = null;
        }
        shell.addListener(SWT.Close, e -> {
            e.doit = false; // closing the window keeps the server running, like leaving the app on the phone
            shell.setVisible(false);
            if (!trayHintShown) {
                trayHintShown = true;
                showBalloon("MC Host", tr("Still running in the tray. Click the icon to open the panel.",
                        "Masih jalan di tray. Klik ikonnya untuk membuka panel."));
            }
        });
        setUpTray(logo);
        listenForShow();
        if (portError != null) message(SWT.ICON_ERROR, portError);
        if (showWindow) show();
        while (!display.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
    }

    private void show() {
        String link = "http://127.0.0.1:" + port + "/local?code=" + PanelApi.newLocalCode();
        if (web == null) {
            Program.launch(link);
            return;
        }
        String now = web.getUrl();
        if (now == null || !now.startsWith("http://127.0.0.1:" + port)) web.setUrl(link);
        shell.setVisible(true);
        shell.setMinimized(false);
        shell.forceActive();
    }

    private void setUpTray(Image logo) {
        Tray tray = display.getSystemTray();
        if (tray == null) return;
        trayItem = new TrayItem(tray, SWT.NONE);
        trayItem.setImage(new Image(display, logo.getImageData().scaledTo(32, 32)));
        trayItem.setToolTipText("MC Host");
        balloon = new ToolTip(shell, SWT.BALLOON | SWT.ICON_INFORMATION);
        trayItem.setToolTip(balloon);
        Menu menu = new Menu(shell, SWT.POP_UP);
        MenuItem open = new MenuItem(menu, SWT.PUSH);
        open.setText(tr("Open panel", "Buka panel"));
        open.addListener(SWT.Selection, e -> show());
        new MenuItem(menu, SWT.SEPARATOR);
        MenuItem quit = new MenuItem(menu, SWT.PUSH);
        quit.setText(tr("Quit MC Host", "Keluar dari MC Host"));
        quit.addListener(SWT.Selection, e -> quit());
        trayItem.addListener(SWT.MenuDetect, e -> menu.setVisible(true));
        trayItem.addListener(SWT.Selection, e -> show());
        trayItem.addListener(SWT.DefaultSelection, e -> show());
        platform.summary = s -> display.asyncExec(() -> {
            if (!trayItem.isDisposed()) trayItem.setToolTipText(clip("MC Host: " + s));
        });
        platform.notifier = (title, text) -> display.asyncExec(() -> showBalloon(title, text));
    }

    private void showBalloon(String title, String text) {
        if (balloon == null || balloon.isDisposed()) return;
        balloon.setText(title);
        balloon.setMessage(text);
        balloon.setVisible(true);
    }

    private void quit() {
        if (host.mc.alive() && message(SWT.ICON_QUESTION | SWT.YES | SWT.NO,
                tr("The server is running. Stop it and quit?", "Server masih jalan. Matikan server lalu keluar?")) != SWT.YES) {
            return;
        }
        shell.setVisible(false);
        if (trayItem != null) trayItem.setToolTipText(tr("MC Host: stopping...", "MC Host: mematikan..."));
        new Thread(() -> {
            stopHost();
            System.exit(0);
        }, "quit").start();
    }

    /** Saves and stops the server once, whether from Quit or from Windows shutting down. */
    private void stopHost() {
        if (!stopping.compareAndSet(false, true)) return;
        host.shutdown(60_000);
        http.stop();
    }

    private int message(int style, String text) {
        MessageBox box = new MessageBox(shell, style);
        box.setText("MC Host");
        box.setMessage(text);
        return box.open();
    }

    private static String clip(String s) {
        return s.length() <= 120 ? s : s.substring(0, 117) + "...";
    }

    // ---- one copy at a time: a second start asks the running one to show its window, then exits

    private void listenForShow() {
        try {
            ServerSocket control = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            IO.writeText(new File(platform.baseDir(), "instance.port"), String.valueOf(control.getLocalPort()));
            Thread t = new Thread(() -> {
                while (true) {
                    try (Socket ignored = control.accept()) {
                        display.asyncExec(this::show);
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "show-listener");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            host.mcLog.panel("instance lock: " + e.getMessage());
        }
    }

    private static boolean showRunningCopy(File base) {
        try {
            int p = Integer.parseInt(IO.readText(new File(base, "instance.port")).trim());
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 500);
            }
            return true;
        } catch (Exception notRunning) {
            return false;
        }
    }
}
