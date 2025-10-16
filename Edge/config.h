#ifndef CONFIG_H
#define CONFIG_H

// =================================================================
// ==              PUSAT KENDALI SEMUA KONFIGURASI                ==
// =================================================================

// --- Konfigurasi Jaringan & Server ---
// Alamat IP & Port server Flask-mu
static const char* SERVER_ADDRESS = "http://192.168.1.88:8080";

// --- Konfigurasi Perangkat & Sensor ---
static const int ECG_PIN = 34;
static const int LO_PLUS_PIN = 25;
static const int LO_MINUS_PIN = 26;
static const int SDN_PIN = 27;
static const int FACTORY_RESET_PIN = 0; // GPIO 0 adalah pin tombol "BOOT"

// --- Konfigurasi Sinyal & Waktu ---
static const int SIGNAL_LENGTH = 1024;
static const int SAMPLING_RATE = 360;
static const char* NTP_SERVER = "id.pool.ntp.org";
static const long GMT_OFFSET_SEC = 0;
static const int DAYLIGHT_OFFSET_SEC = 0;


// --- Konfigurasi Filter ---
static const int FILTER_ORDER = 3;
static const float b_beat[] = {0.18475754, -0.11728189, -0.11728189, 0.18475754};
static const float a_beat[] = {1.0, -2.22498772, 1.90591593, -0.63004815};
static const float b_afib[] = {0.05193931, 0.00769507, -0.00769507, -0.05193931};
static const float a_afib[] = {1.0, -2.4834211, 2.1311083, -0.63914615};

// --- Konfigurasi BLE Operasional ---
#define OP_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define ECG_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// --- Konfigurasi Manajemen Daya ---
static const long INACTIVITY_TIMEOUT_MS = 60000; // 1 menit
static const long longPressDuration = 3000; // 3 detik

#endif
