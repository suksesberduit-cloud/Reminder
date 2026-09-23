package id.reminder.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

/**
 * Dipicu AlarmManager tiap 5 menit (exact alarm, tembus Doze/Battery Saver kalau
 * izin sudah diberikan). Selalu coba (re)start ScreenUnlockService, lalu jadwalkan
 * dirinya sendiri lagi 5 menit ke depan (self-rescheduling).
 */
public class WatchdogReceiver extends BroadcastReceiver {
    private static final int WATCHDOG_REQUEST_CODE = 9003;
    private static final long INTERVAL_MS = 5 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences("reminder_prefs", 0)
            .getBoolean("enabled", false);
        if (!enabled) return;

        Intent serviceIntent = new Intent(context, ScreenUnlockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        schedule(context);
    }

    public static void schedule(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmMgr == null) return;

        Intent watchdogIntent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, WATCHDOG_REQUEST_CODE, watchdogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS;
        boolean canExact = true;
        if (Build.VERSION.SDK_INT >= 31) {
            canExact = alarmMgr.canScheduleExactAlarms();
        }

        try {
            if (canExact) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmMgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
        } catch (SecurityException e) {
            alarmMgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        }
    }
}
