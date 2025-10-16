#include "utils.h"
#include "config.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLE2902.h>
#include "time.h"
#include "ButterworthFilter.h"

// Deklarasi eksternal untuk variabel global dari Edge.ino
extern bool isSensorActive;
extern ButterworthFilter* beatFilter;
extern ButterworthFilter* afibFilter;
extern int bufferIndex;
extern unsigned long lastActivityTime;
extern unsigned long buttonPressStartTime;
extern bool longPressTriggered;

String getDeviceIdentity() {
    String mac = WiFi.macAddress();
    JsonDocument doc;
    doc["mac_address"] = mac.c_str();
    String payload;
    serializeJson(doc, payload);

    HTTPClient http;
    String registerUrl = String(SERVER_ADDRESS) + "/api/register-device";
    http.begin(registerUrl);
    http.addHeader("Content-Type", "application/json");
    
    Serial.printf("[HTTP] Meminta Device ID dari server dengan MAC: %s\n", mac.c_str());

    int httpCode = http.POST(payload);
    if (httpCode == 200 || httpCode == 201) {
        String response = http.getString();
        deserializeJson(doc, response);
        if (!doc["device_id"].isNull()) {
            return doc["device_id"].as<String>();
        }
    } else {
        Serial.printf("[HTTP-ERROR] Gagal registrasi, kode: %d, error: %s\n", httpCode, http.errorToString(httpCode).c_str());
    }
    return ""; // Kembalikan string kosong jika gagal
}

void setupOperationalBLE(BLEServer* &pServer, BLECharacteristic* &pCharacteristic, BLEServerCallbacks* callbacks) {
    String mac = WiFi.macAddress();
    String macSuffix = mac.substring(12);
    macSuffix.replace(":", "");
    String deviceName = "ECG_Monitor_" + macSuffix;

    BLEDevice::init(deviceName.c_str());
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(callbacks);
    BLEService *pService = pServer->createService(OP_SERVICE_UUID);
    pCharacteristic = pService->createCharacteristic(
                        ECG_CHARACTERISTIC_UUID,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
    pCharacteristic->addDescriptor(new BLE2902());
    pService->start();
    BLEDevice::getAdvertising()->addServiceUUID(OP_SERVICE_UUID);
    BLEDevice::startAdvertising();
    Serial.println("[BLE] Mode Operasional Aktif. Menunggu koneksi...");
}

bool isSignalValid(int loPlusPin, int loMinusPin) {
    return (digitalRead(loPlusPin) == LOW && digitalRead(loMinusPin) == LOW);
}

String getTimestamp() {
    time_t now;
    struct tm timeinfo;
    time(&now);
    if (timeinfo.tm_year < 124) { // Cek jika waktu belum valid (di bawah tahun 2024)
        return "0000-00-00T00:00:00+00:00";
    }
    const long wibOffset = 7 * 3600;
    time_t wibTime = now + wibOffset;
    gmtime_r(&wibTime, &timeinfo);
    char timeString[30];
    strftime(timeString, sizeof(timeString), "%Y-%m-%dT%H:%M:%S+07:00", &timeinfo);
    return String(timeString);
}

void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* beatBuffer, float* afibBuffer, int length) {
    if (WiFi.status() == WL_CONNECTED) {
        JsonDocument doc;
        doc["device_id"] = deviceId;
        doc["timestamp"] = timestamp;
        JsonArray ecgBeatData = doc["ecg_beat_data"].to<JsonArray>();
        for (int i = 0; i < length; i++) { ecgBeatData.add(beatBuffer[i]); }
        JsonArray ecgAfibData = doc["ecg_afib_data"].to<JsonArray>();
        for (int i = 0; i < length; i++) { ecgAfibData.add(afibBuffer[i]); }
        String jsonString;
        serializeJson(doc, jsonString);
        HTTPClient http;
        http.begin(url);
        http.addHeader("Content-Type", "application/json");
        http.setTimeout(10000);
        int httpCode = http.POST(jsonString);
        if (httpCode > 0) {
            String response = http.getString();
            Serial.print("[WIFI-SERVER] Balasan: ");
            Serial.println(response);
        } else {
            Serial.print("[WIFI-ERROR] Gagal kirim data: ");
            Serial.println(http.errorToString(httpCode));
        }
        http.end();
    } else {
        Serial.println("[WIFI-ERROR] Koneksi putus, data tidak terkirim.");
    }
}

void sensorSleep() {
    if (isSensorActive) {
        Serial.println("[POWER] Sensor dinonaktifkan untuk hemat daya.");
        digitalWrite(SDN_PIN, LOW);
        isSensorActive = false;
    }
}

void sensorWakeUp() {
    if (!isSensorActive) {
        Serial.println("[POWER] Sensor diaktifkan.");
        digitalWrite(SDN_PIN, HIGH);
        isSensorActive = true;
        beatFilter->reset();
        afibFilter->reset();
        bufferIndex = 0;
        lastActivityTime = millis();
    }
}

void handleFactoryReset() {
  if (digitalRead(FACTORY_RESET_PIN) == LOW) {
    if (buttonPressStartTime == 0) {
      buttonPressStartTime = millis();
      longPressTriggered = false;
      Serial.println("[RESET] Tombol reset terdeteksi, tahan selama 3 detik...");
    }
    if (!longPressTriggered && (millis() - buttonPressStartTime > longPressDuration)) {
      Serial.println("\n[RESET] Melakukan factory reset...");
      WiFi.disconnect(true, true); // Menghapus kredensial Wi-Fi
      Serial.println("[RESET] Kredensial Wi-Fi dihapus. Perangkat akan restart.");
      delay(1000);
      ESP.restart();
      longPressTriggered = true;
    }
  } else {
    if (buttonPressStartTime != 0) {
      Serial.println("[RESET] Batal.");
      buttonPressStartTime = 0;
    }
  }
}
