#ifndef CONFIG_H
#define CONFIG_H

// =================================================================
// ==              PUSAT KENDALI SEMUA KONFIGURASI                ==
// =================================================================

// --- Konfigurasi Jaringan & Server ---
static const char* DEFAULT_SERVER_ADDRESS = "http://http://127.0.0.1:5000";

// --- Konfigurasi Perangkat & Sensor ---
static const int ECG_PIN = 34;
static const int LO_PLUS_PIN = 25;
static const int LO_MINUS_PIN = 26;
static const int SDN_PIN = 27;

// --- Konfigurasi Sinyal & Waktu ---
static const int SIGNAL_LENGTH = 1024;
static const int SAMPLING_RATE = 360;
static const char* NTP_SERVER = "pool.ntp.org";
static const long GMT_OFFSET_SEC = 0;
static const int DAYLIGHT_OFFSET_SEC = 0;

// --- Konfigurasi Filter ---
static const int FILTER_ORDER = 3;
// Koefisien #1: Untuk Beat Detection (Bandpass 0.5-40 Hz)
static const float b_beat[] = {0.18475754, -0.11728189, -0.11728189, 0.18475754};
static const float a_beat[] = {1.0, -2.22498772, 1.90591593, -0.63004815};
// Koefisien #2: Untuk AFib Detection (Bandpass 0.5-20 Hz)
static const float b_afib[] = {0.05193931, 0.00769507, -0.00769507, -0.05193931};
static const float a_afib[] = {1.0, -2.4834211, 2.1311083, -0.63914615};

// --- Konfigurasi BLE Operasional ---
// #define tidak perlu 'static' karena bukan variabel
#define OP_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define ECG_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// --- Konfigurasi BLE Provisioning ---
#define PROV_SERVICE_UUID         "a8a9e425-56d3-4e65-a818-8f5b822646b9"
#define MAC_CHARACTERISTIC_UUID   "f8e1762d-978d-4a3d-8da1-831d683a523a"
#define ID_CHARACTERISTIC_UUID    "2d334336-6e06-4547-9051-1e35553e13d1"

// --- Konfigurasi Manajemen Daya ---
static const long INACTIVITY_TIMEOUT_MS = 60000; // 1 menit

#endif