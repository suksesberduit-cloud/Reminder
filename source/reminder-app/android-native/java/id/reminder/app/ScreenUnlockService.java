package id.reminder.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

public class ScreenUnlockService extends Service {

    private BroadcastReceiver unlockReceiver;
    private static final String CHANNEL_ID = "reminder_service_channel";
    private static final String TRIGGER_CHANNEL_ID = "reminder_trigger_channel";
    private static final int SERVICE_NOTIF_ID = 9001;
    private static final int TRIGGER_NOTIF_ID = 9002;
    private static final int WATCHDOG_REQUEST_CODE = 9003;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(SERVICE_NOTIF_ID, buildServiceNotification());

        unlockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    fireReminderNotification();
                }
            }
        };
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

        scheduleWatchdog();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Beberapa OEM (Xiaomi/Oppo/Vivo/Realme) mematikan service saat app
        // di-swipe dari recent apps, walau statusnya foreground service.
        // Segera restart diri sendiri begitu itu terjadi.
        Intent restartIntent = new Intent(getApplicationContext(), ScreenUnlockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(restartIntent);
        } else {
            getApplicationContext().startService(restartIntent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(unlockReceiver);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Jadwalkan watchdog exact-alarm pertama kali. */
    private void scheduleWatchdog() {
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", 0);
        if (!prefs.getBoolean("enabled", false)) return;
        WatchdogReceiver.schedule(getApplicationContext());
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mgr = getSystemService(NotificationManager.class);

            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID, "Status Reminder", NotificationManager.IMPORTANCE_MIN
            );
            mgr.createNotificationChannel(serviceChannel);

            NotificationChannel triggerChannel = new NotificationChannel(
                TRIGGER_CHANNEL_ID, "Notifikasi Reminder", NotificationManager.IMPORTANCE_HIGH
            );
            mgr.createNotificationChannel(triggerChannel);
        }
    }

    private Notification buildServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reminder aktif")
            .setContentText("Memantau saat layar dibuka kunci")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build();
    }

    /** Bangun Intent tujuan berdasarkan mode yang tersimpan: buka app biasa, atau buka URL di browser tertentu. */
    private Intent buildLaunchIntent() {
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", 0);
        String mode = prefs.getString("mode", "app");
        String targetPackage = prefs.getString("targetPackage", "");

        if ("website".equals(mode)) {
            String url = prefs.getString("websiteUrl", "");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!targetPackage.isEmpty()) {
                intent.setPackage(targetPackage);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent == null) {
            launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + targetPackage));
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launchIntent;
    }

    private void fireReminderNotification() {
        SharedPreferences prefs = getSharedPreferences("reminder_prefs", 0);
        if (!prefs.getBoolean("enabled", false)) return;

        String title = prefs.getString("title", "Reminder");
        String body = prefs.getString("body", "Ketuk untuk membuka aplikasi");

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, TRIGGER_NOTIF_ID, buildLaunchIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notif = new NotificationCompat.Builder(this, TRIGGER_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build();

        NotificationManager mgr = getSystemService(NotificationManager.class);
        mgr.notify(TRIGGER_NOTIF_ID, notif);
    }
}
