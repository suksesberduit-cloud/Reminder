package id.reminder.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(
    name = "Reminder",
    permissions = {
        @Permission(strings = { android.Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications")
    }
)
public class ReminderPlugin extends Plugin {

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionForAlias("notifications", call, "permCallback");
        } else {
            call.resolve();
        }
    }

    @PermissionCallback
    private void permCallback(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void startService(PluginCall call) {
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences("reminder_prefs", 0);
        prefs.edit()
            .putString("mode", call.getString("mode", "app"))
            .putString("targetPackage", call.getString("targetPackage", ""))
            .putString("websiteUrl", call.getString("websiteUrl", ""))
            .putString("title", call.getString("title", "Reminder"))
            .putString("body", call.getString("body", "Ketuk untuk membuka aplikasi"))
            .putBoolean("enabled", true)
            .apply();

        Intent intent = new Intent(ctx, ScreenUnlockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent);
        } else {
            ctx.startService(intent);
        }

        promptExactAlarmIfNeeded();
        call.resolve();
    }

    private void promptExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < 31) return;
        android.app.AlarmManager am = (android.app.AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        if (am != null && !am.canScheduleExactAlarms()) {
            Intent intent = new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM");
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        }
    }

    @PluginMethod
    public void stopService(PluginCall call) {
        Context ctx = getContext();
        ctx.getSharedPreferences("reminder_prefs", 0).edit()
            .putBoolean("enabled", false).apply();
        ctx.stopService(new Intent(ctx, ScreenUnlockService.class));
        call.resolve();
    }

    @PluginMethod
    public void isBatteryOptimizationIgnored(PluginCall call) {
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        boolean ignored = pm.isIgnoringBatteryOptimizations(getContext().getPackageName());
        JSObject ret = new JSObject();
        ret.put("value", ignored);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestBatteryOptimizationExemption(PluginCall call) {
        Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getContext().getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        call.resolve();
    }

    /**
     * Sama seperti pickInstalledApp, tapi difilter hanya app yang bisa
     * menangani link web (browser) — dipakai untuk mode "Aplikasi Lainnya / Website".
     */
    @PluginMethod
    public void pickBrowserApp(PluginCall call) {
        call.setKeepAlive(true);

        getActivity().runOnUiThread(() -> {
            PackageManager pm = getContext().getPackageManager();
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            List<ResolveInfo> apps = pm.queryIntentActivities(browserIntent, 0);
            Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));

            final String[] labels = new String[apps.size()];
            final String[] packages = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                ResolveInfo ri = apps.get(i);
                labels[i] = ri.loadLabel(pm).toString();
                packages[i] = ri.activityInfo.packageName;
            }

            new AlertDialog.Builder(getActivity())
                .setTitle("Pilih Browser")
                .setItems(labels, (dialog, which) -> {
                    JSObject ret = new JSObject();
                    ret.put("packageName", packages[which]);
                    ret.put("appName", labels[which]);
                    call.resolve(ret);
                })
                .setOnCancelListener(dialog -> call.reject("Dibatalkan pengguna"))
                .show();
        });
    }

    /**
     * Menampilkan dialog native berisi SEMUA aplikasi terinstall yang bisa dibuka
     * (punya launcher icon), diurutkan alfabetis. User tap salah satu -> resolve
     * dengan packageName + appName. User cancel (tap luar dialog) -> reject.
     */
    @PluginMethod
    public void pickInstalledApp(PluginCall call) {
        call.setKeepAlive(true);

        getActivity().runOnUiThread(() -> {
            PackageManager pm = getContext().getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
            Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));

            final String[] labels = new String[apps.size()];
            final String[] packages = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) {
                ResolveInfo ri = apps.get(i);
                labels[i] = ri.loadLabel(pm).toString();
                packages[i] = ri.activityInfo.packageName;
            }

            new AlertDialog.Builder(getActivity())
                .setTitle("Pilih Aplikasi Tujuan")
                .setItems(labels, (dialog, which) -> {
                    JSObject ret = new JSObject();
                    ret.put("packageName", packages[which]);
                    ret.put("appName", labels[which]);
                    call.resolve(ret);
                })
                .setOnCancelListener(dialog -> call.reject("Dibatalkan pengguna"))
                .show();
        });
    }

    @PluginMethod
    public void isRunning(PluginCall call) {
        boolean enabled = getContext().getSharedPreferences("reminder_prefs", 0)
            .getBoolean("enabled", false);
        JSObject ret = new JSObject();
        ret.put("value", enabled);
        call.resolve(ret);
    }
}
