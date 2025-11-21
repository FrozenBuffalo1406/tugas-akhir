#ifndef CONFIG_H
#define CONFIG_H

// --- Konfigurasi Jaringan & Server ---
static const char* SERVER_ADDRESS = "https://ecg-detection.developedbyme.my.id/api/v1";

// --- Konfigurasi Perangkat & Sensor ---
static const int ECG_PIN = 34;
static const int LO_PLUS_PIN = 25;
static const int LO_MINUS_PIN = 26;
static const int SDN_PIN = 27;
static const int FACTORY_RESET_PIN = 0; // GPIO 0 adalah pin tombol "BOOT"

// --- display ---
static const int OLED_SDA_PIN = 21; // Ganti sesuai pinout lo
static const int OLED_SCL_PIN = 22; // Ganti sesuai pinout lo
static const int SCREEN_WIDTH = 128;
static const int SCREEN_HEIGHT = 64;
static const int PLOT_HEIGHT = 48;

// --- buttons ---
static const int BUTTON_1_PIN = 32; // Ganti sesuai pinout lo
static const int BUTTON_2_PIN = 33; // Ganti sesuai pinout lo

// --- Konfigurasi Sinyal ---
static const int SIGNAL_LENGTH = 1024; 
static const int SAMPLING_RATE = 360;

// time config
static const long GMT_OFFSET_SEC = 7 * 3600; 
static const int DAYLIGHT_OFFSET_SEC = 0;

// --- Konfigurasi Filter ---
static const int FILTER_ORDER = 3;
static const float b_beat[] = {0.18475754, -0.11728189, -0.11728189, 0.18475754};
static const float a_beat[] = {1.0, -2.22498772, 1.90591593, -0.63004815};
static const float EMA_ALPHA = 0.35;
static const int NOTCH_FILTER_ORDER = 2; 
static const float b_notch[] = {0.96545, -1.0964, 0.96545};
static const float a_notch[] = {1.0, -1.0964, 0.9309};

// --- Konfigurasi Manajemen Daya ---
static const long INACTIVITY_TIMEOUT_MS = 60000; // 1 menit
static const long MEDIUM_PRESS_DURATION_MS = 3000;
static const long LONG_PRESS_DURATION_MS = 7000;

#define SERVICE_UUID        BLEUUID((uint16_t)0x180A) // Standard Device Information Service
#define CHAR_MAC_UUID       BLEUUID((uint16_t)0x2A23) // Standard System ID -> Kita isi MAC Address
#define CHAR_DEVICE_ID_UUID BLEUUID((uint16_t)0x2A24) // Standard Model Number -> Kita isi Device ID "ECG_xxx"

#endif

