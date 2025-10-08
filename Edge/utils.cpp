#include "utils.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "time.h"

// UUID dan Class Callback tetap di sini karena sangat spesifik untuk implementasi BLE ini
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Variabel deviceConnected dan class callback bisa dideklarasikan di sini
// dan diakses oleh fungsi di file ini.
extern bool deviceConnected; // Gunakan 'extern' untuk memberitahu bahwa variabel ini ada di file lain
class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) { deviceConnected = true; Serial.println("[BLE] Perangkat terhubung"); }
    void onDisconnect(BLEServer* pServer) { deviceConnected = false; Serial.println("[BLE] Perangkat terputus"); pServer->getAdvertising()->start(); }
};


void setupBLE(BLEServer* &pServer, BLECharacteristic* &pCharacteristic) {
  Serial.println("Inisialisasi BLE...");
  BLEDevice::init("ESP32_ECG_Monitor_TA");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Menunggu koneksi dari client (HP)...");
}

bool isSignalValid(int loPlusPin, int loMinusPin) {
  // Sinyal dianggap valid jika KEDUA pin LO+ dan LO- berada dalam kondisi LOW
  if (digitalRead(loPlusPin) == HIGH || digitalRead(loMinusPin) == HIGH) {
    return false; // Ada elektroda yang terlepas
  }
  return true; // Semua elektroda terpasang
}

float readAndFilterECG(ButterworthFilter &filter, int pin) {
  float rawValue = analogRead(pin);
  return filter.update(rawValue);
}

// Ganti seluruh fungsi lama dengan yang ini
void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* buffer, int length) {
  // Hanya kirim jika WiFi terhubung
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WIFI] Buffer penuh. Mengirim data ke server...");

    // Menggunakan ArduinoJson v7 (tidak perlu hitung kapasitas)
    JsonDocument doc;

    // Menambahkan semua data ke dokumen JSON
    doc["device_id"] = deviceId;
    doc["timestamp"] = timestamp;
    JsonArray ecgData = doc.createNestedArray("ecg_data");
    for (int i = 0; i < length; i++) {
      ecgData.add(buffer[i]);
    }
    
    // Mengubah dokumen JSON menjadi string
    String jsonString;
    serializeJson(doc, jsonString);

    // Proses pengiriman via HTTP POST
    HTTPClient http;
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    int httpResponseCode = http.POST(jsonString);

    if (httpResponseCode > 0) {
      String response = http.getString();
      Serial.print("[WIFI-SERVER] Balasan: ");
      Serial.println(response);
    } else {
      Serial.print("[WIFI-ERROR] Gagal mengirim data. Kode Error: ");
      Serial.println(httpResponseCode);
    }
    http.end();
    
  } else {
    Serial.println("[WIFI-ERROR] Koneksi Wi-Fi terputus.");
  }
}

String getTimestamp() {
  time_t now;
  struct tm timeinfo;
  
  time(&now);
  gmtime_r(&now, &timeinfo);
  
  if (timeinfo.tm_year < 124) {
    Serial.println("Waktu belum tersinkronisasi dengan benar");
    return "0000-00-00T00:00:00+00:00";
  }

  const long wibOffset = 7 * 3600;
  time_t wibTime = now + wibOffset;
  gmtime_r(&wibTime, &timeinfo);

  char timeString[30];
  
  strftime(timeString, sizeof(timeString), "%Y-%m-%dT%H:%M:%S+07:00", &timeinfo);
  
  return String(timeString);
}