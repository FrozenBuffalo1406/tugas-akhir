#include <WiFi.h>

// Include Class dan Utilities
#include "ButterworthFilter.h"
#include "utils.h"
#include "time.h"

// Konfigurasi sekarang terpusat di sini
const char* WIFI_SSID = "OIOI SPACE_5G";
const char* WIFI_PASS = "paloymaloy";
const char* SERVER_URL = "http://192.168.1.10:5000/api/analyze-ecg";

const char* DEVICE_ID = "ESP32_ECG_01";

const int ECG_PIN = 34;
const int LO_PLUS_PIN = 25;  // Terhubung ke pin LO+ di AD8232
const int LO_MINUS_PIN = 26; // Terhubung ke pin LO- di AD8232
const int SDN_PIN = 27;

const int SIGNAL_LENGTH = 1024;
const int SAMPLING_RATE = 360;

const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 0;
const int daylightOffset_sec = 0;

ButterworthFilter ecgFilter;

BLEServer* pServer = NULL;
BLECharacteristic* pCharacteristic = NULL;
bool deviceConnected = false; 

float ecgBuffer[SIGNAL_LENGTH];
int bufferIndex = 0;
unsigned long lastSampleTime = 0;
const long sampleInterval = 1000 / SAMPLING_RATE;

void setup() {
  Serial.begin(115200);
  delay(1000);

  pinMode(LO_PLUS_PIN, INPUT);
  pinMode(LO_MINUS_PIN, INPUT);
  pinMode(SDN_PIN, OUTPUT);
  digitalWrite(SDN_PIN, HIGH);

  // --- Setup WiFi ---
  Serial.printf("Menyambung ke WiFi: %s\n", WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n[WIFI] WiFi tersambung!");
  Serial.print("[WIFI] Alamat IP ESP32: ");
  Serial.println(WiFi.localIP());

  // --- 2. Sinkronisasi Waktu NTP ---
  Serial.println("Sinkronisasi waktu via NTP...");
  configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  struct tm timeinfo;
  while (!getLocalTime(&timeinfo)) {
    Serial.print(".");
    delay(1000);
  }
  //debug comment for: ntp 
  Serial.println("\n[NTP] Waktu berhasil disinkronisasi!");
  
  setupBLE(pServer, pCharacteristic);
}

void loop() {
  if (millis() - lastSampleTime >= sampleInterval) {
    lastSampleTime = millis();

    if (!isSignalValid(LO_PLUS_PIN, LO_MINUS_PIN)) {
      Serial.println("[WARNING] Elektroda terlepas! Tidak ada data yang direkam.");
      // Keluar dari blok if ini dan tunggu siklus berikutnya
      // Ini mencegah data "sampah" masuk ke buffer
      return; 
    }

    if (bufferIndex < SIGNAL_LENGTH) {
      // Manggil fungsi dari utils dengan parameter
      float filteredValue = readAndFilterECG(ecgFilter, ECG_PIN);
      ecgBuffer[bufferIndex] = filteredValue;

      if (deviceConnected) {
        pCharacteristic->setValue((uint8_t*)&filteredValue, 4);
        pCharacteristic->notify();
      }
      bufferIndex++;
    }
  }

  if (bufferIndex >= SIGNAL_LENGTH) {

    String timestamp = getTimestamp();

    // Manggil fungsi dari utils dengan parameter
    sendDataToServer(SERVER_URL, DEVICE_ID, timestamp.c_str(), ecgBuffer, SIGNAL_LENGTH);
    bufferIndex = 0;
    ecgFilter.reset();
    Serial.println("\n[INFO] Buffer direset. Memulai pengumpulan data baru...");
  }
}