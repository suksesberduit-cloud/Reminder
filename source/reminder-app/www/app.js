// app.js — jembatan UI ke plugin native ReminderPlugin

const pickAppBtn = document.getElementById('pickAppBtn');
const selectedAppText = document.getElementById('selectedAppText');
const toggleWebsiteBtn = document.getElementById('toggleWebsiteBtn');
const websiteSection = document.getElementById('websiteSection');
const websiteUrlInput = document.getElementById('websiteUrl');
const pickBrowserBtn = document.getElementById('pickBrowserBtn');
const selectedBrowserText = document.getElementById('selectedBrowserText');
const batteryBtn = document.getElementById('batteryBtn');
const batteryStatusText = document.getElementById('batteryStatusText');
const masterToggle = document.getElementById('masterToggle');
const notifTitle = document.getElementById('notifTitle');
const notifBody = document.getElementById('notifBody');
const saveBtn = document.getElementById('saveBtn');
const statusText = document.getElementById('statusText');

const STORAGE_KEY = 'reminder_config_v1';

function loadConfig() {
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw ? JSON.parse(raw) : {
    enabled: false,
    mode: 'app',
    targetPackage: '',
    targetAppName: '',
    websiteUrl: '',
    title: 'Waktunya fokus baca 📚',
    body: 'Ketuk untuk lanjut baca bukumu',
  };
}

let currentConfig = loadConfig();
if (!currentConfig.mode) currentConfig.mode = 'app';

function renderSelectedApp() {
  if (currentConfig.mode === 'website') {
    selectedAppText.textContent = currentConfig.targetAppName
      ? 'Website via ' + currentConfig.targetAppName + ' (' + (currentConfig.websiteUrl || 'URL belum diisi') + ')'
      : 'Belum ada aplikasi dipilih';
  } else {
    selectedAppText.textContent = currentConfig.targetAppName
      ? currentConfig.targetAppName + ' (' + currentConfig.targetPackage + ')'
      : 'Belum ada aplikasi dipilih';
  }
}

function renderSelectedBrowser() {
  selectedBrowserText.textContent = (currentConfig.mode === 'website' && currentConfig.targetAppName)
    ? currentConfig.targetAppName + ' (' + currentConfig.targetPackage + ')'
    : 'Belum pilih browser';
}

function applyConfigToUI() {
  masterToggle.checked = currentConfig.enabled;
  notifTitle.value = currentConfig.title;
  notifBody.value = currentConfig.body;
  websiteUrlInput.value = currentConfig.websiteUrl || '';
  websiteSection.style.display = (currentConfig.mode === 'website') ? 'block' : 'none';
  renderSelectedApp();
  renderSelectedBrowser();
}

async function callNativePlugin(method, options) {
  options = options || {};
  const plugin = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.Reminder;
  if (!plugin) {
    console.warn('Plugin native Reminder belum tersedia (jalan di browser biasa?)');
    return null;
  }
  return plugin[method](options);
}

async function refreshBatteryStatus() {
  const res = await callNativePlugin('isBatteryOptimizationIgnored');
  batteryStatusText.textContent = (res && res.value)
    ? 'Optimasi Baterai: ✅ Sudah dimatikan'
    : 'Optimasi Baterai: ❌ Masih aktif (berisiko notif hilang)';
}

batteryBtn.addEventListener('click', async () => {
  await callNativePlugin('requestBatteryOptimizationExemption');
});

document.addEventListener('visibilitychange', () => {
  if (!document.hidden) refreshBatteryStatus();
});

toggleWebsiteBtn.addEventListener('click', () => {
  const showing = websiteSection.style.display === 'block';
  websiteSection.style.display = showing ? 'none' : 'block';
  if (!showing) {
    currentConfig.mode = 'website';
    renderSelectedApp();
  }
});

pickAppBtn.addEventListener('click', async () => {
  try {
    const result = await callNativePlugin('pickInstalledApp');
    if (result && result.packageName) {
      currentConfig.mode = 'app';
      currentConfig.targetPackage = result.packageName;
      currentConfig.targetAppName = result.appName;
      websiteSection.style.display = 'none';
      renderSelectedApp();
    }
  } catch (e) {
    console.log('Pemilihan app dibatalkan:', e);
  }
});

pickBrowserBtn.addEventListener('click', async () => {
  try {
    const result = await callNativePlugin('pickBrowserApp');
    if (result && result.packageName) {
      currentConfig.mode = 'website';
      currentConfig.targetPackage = result.packageName;
      currentConfig.targetAppName = result.appName;
      renderSelectedBrowser();
      renderSelectedApp();
    }
  } catch (e) {
    console.log('Pemilihan browser dibatalkan:', e);
  }
});

saveBtn.addEventListener('click', async () => {
  currentConfig.enabled = masterToggle.checked;
  currentConfig.title = notifTitle.value.trim() || 'Reminder';
  currentConfig.body = notifBody.value.trim() || 'Ketuk untuk membuka aplikasi';
  currentConfig.websiteUrl = websiteUrlInput.value.trim();

  if (currentConfig.enabled) {
    if (currentConfig.mode === 'website') {
      if (!currentConfig.websiteUrl) {
        statusText.textContent = 'Status: Isi alamat website dulu';
        return;
      }
      if (!currentConfig.targetPackage) {
        statusText.textContent = 'Status: Pilih browser dulu';
        return;
      }
    } else if (!currentConfig.targetPackage) {
      statusText.textContent = 'Status: Pilih aplikasi tujuan dulu';
      return;
    }
  }

  localStorage.setItem(STORAGE_KEY, JSON.stringify(currentConfig));

  if (currentConfig.enabled) {
    await callNativePlugin('requestPermissions');
    await callNativePlugin('startService', currentConfig);
    statusText.textContent = 'Status: Aktif — memantau unlock layar';
  } else {
    await callNativePlugin('stopService');
    statusText.textContent = 'Status: Nonaktif';
  }
});

(async function init() {
  applyConfigToUI();
  refreshBatteryStatus();
  const running = await callNativePlugin('isRunning');
  statusText.textContent = (running && running.value)
    ? 'Status: Aktif — memantau unlock layar'
    : 'Status: Nonaktif';
})();
