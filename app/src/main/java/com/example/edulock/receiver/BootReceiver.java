package com.example.edulock.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.edulock.service.BlockOverlayService;

/**
 * Restarts BlockOverlayService on phone boot if a connection code is saved,
 * so blocking enforcement survives a full reboot.
 *
 * Already declared in your AndroidManifest.xml as:
 *
 *   <receiver android:name=".receiver.BootReceiver"
 *             android:enabled="true"
 *             android:exported="true">
 *       <intent-filter>
 *           <action android:name="android.intent.action.BOOT_COMPLETED" />
 *           <category android:name="android.intent.category.DEFAULT" />
 *       </intent-filter>
 *   </receiver>
 *
 * Note: your manifest currently has TWO copies of this receiver declaration.
 * That's harmless but redundant — feel free to delete one of them.
 *
 * If this BootReceiver is ALSO responsible for kicking off other services
 * (e.g. UsageMonitorService) keep that logic and just add the
 * BlockOverlayService.start() call alongside it.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS_NAME    = "EduLock";
    private static final String KEY_CONN_CODE = "connection_code";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String code = prefs.getString(KEY_CONN_CODE, null);
        if (code == null) return;

        // Start the foreground service that will reattach the Firebase
        // listener and resume block enforcement.
        Intent svc = new Intent(ctx, BlockOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc);
        } else {
            ctx.startService(svc);
        }
    }
}
