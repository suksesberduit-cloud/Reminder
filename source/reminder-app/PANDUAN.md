# Panduan Setup — Reminder App

## 1. Buat project Capacitor
```bash
npm init -y
npm install @capacitor/core @capacitor/cli @capacitor/android
npx cap init Reminder id.reminder.app --web-dir=www
```
Salin isi folder `www/` (index.html, app.js, manifest.json) ke folder `www/` project kamu.

## 2. Tambah platform Android
```bash
npx cap add android
```

## 3. Pasang file native
Salin 4 file dari folder `android-native/java/id/reminder/app/`:
- `ReminderPlugin.kt`
- `ScreenUnlockService.kt`
- `BootReceiver.kt`

ke:
```
android/app/src/main/java/id/reminder/app/
```
(buat foldernya kalau belum ada — path harus persis sesuai `appId` di `capacitor.config.ts`).

## 4. Daftarkan plugin custom
Buka `MainActivity.java` / `.kt` di `android/app/src/main/java/id/reminder/app/`, tambahkan sebelum `super.onCreate`:
```kotlin
registerPlugin(ReminderPlugin::class.java)
```

## 5. Edit AndroidManifest.xml
Buka `android/app/src/main/AndroidManifest.xml`, tempel isi dari
`android-native/AndroidManifest-additions.xml` sesuai lokasinya (permission di luar `<application>`, service+receiver di dalam `<application>`).

## 6. Sinkronkan
```bash
npx cap sync android
```

## 7. Setup GitHub Actions
Taruh `build-apk.yml` ke `.github/workflows/build-apk.yml` di repo kamu, push ke `main`.
APK hasil build muncul di tab **Actions → Artifacts**.

## 8. Setelah install APK di HP
- Buka app, aktifkan toggle "Aktifkan Reminder", pilih app baca tujuan, simpan.
- App akan minta izin notifikasi (Android 13+) — izinkan.
- **Penting:** buka Settings HP → Battery → cari "Reminder" → set ke **"Unrestricted"** / matikan optimasi baterai untuk app ini. Tanpa ini, HP seperti Xiaomi/Oppo/Vivo sering membunuh foreground service walau sudah pakai `START_STICKY`.

## Cara kerja singkat
1. Toggle ON → `ScreenUnlockService` mulai jalan sebagai foreground service (ada notifikasi kecil permanen, transparan bahwa service aktif).
2. Service mendaftarkan listener `ACTION_USER_PRESENT` (broadcast resmi Android saat layar dibuka kunci).
3. Tiap event itu terjadi → service munculkan notifikasi baca.
4. Notifikasi diklik → intent buka app target lewat `getLaunchIntentForPackage`. Kalau app belum terpasang, fallback buka halaman Play Store-nya.
5. `BootReceiver` (didaftarkan statis, ini yang diizinkan Android) menyalakan ulang service otomatis setelah HP restart — **hanya jika** kamu sebelumnya mengaktifkan toggle.
