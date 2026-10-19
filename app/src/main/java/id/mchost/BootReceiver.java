package id.mchost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

import id.mchost.core.Config;

/** Starts the panel after reboot when "Jalan saat HP dinyalakan" is on. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !"android.intent.action.QUICKBOOT_POWERON".equals(a)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Config cfg = new Config(new File(ctx.getFilesDir(), "panel.properties"));
        if (cfg.getBool(Config.BOOT_START)) ServerService.start(ctx);
    }
}
