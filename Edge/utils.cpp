#include "utils.h"
#include "config.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <BLEDevice.h>
#include <BLE2902.h>
#include "time.h"

void setupOperationalBLE(BLEServer*& pServer, BLECharacteristic*& pCharacteristic, BLEServerCallbacks* callbacks) {
  String mac = WiFi.macAddress();
  String macSuffix = mac.substring(12);
  macSuffix.replace(":", "");
  String deviceName = "ECG_Monitor_" + macSuffix;

  BLEDevice::init(deviceName.c_str());
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(callbacks);
  BLEService* pService = pServer->createService(OP_SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
    ECG_CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY);
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  BLEDevice::getAdvertising()->addServiceUUID(OP_SERVICE_UUID);
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Mode Operasional Aktif. Menunggu koneksi...");
}

void startBleProvisioning(BLEServer*& pServer, BLECharacteristicCallbacks* idCallbacks) {
  String mac = WiFi.macAddress();
  String macSuffix = mac.substring(12);
  macSuffix.replace(":", "");
  String deviceName = "PROV_" + macSuffix;

  BLEDevice::init(deviceName.c_str());
  pServer = BLEDevice::createServer();
  BLEService* pProvService = pServer->createService(PROV_SERVICE_UUID);
  BLECharacteristic* pMacCharacteristic = pProvService->createCharacteristic(MAC_CHARACTERISTIC_UUID, BLECharacteristic::PROPERTY_READ);
  pMacCharacteristic->setValue(mac.c_str());
  BLECharacteristic* pIdCharacteristic = pProvService->createCharacteristic(ID_CHARACTERISTIC_UUID, BLECharacteristic::PROPERTY_WRITE);
  pIdCharacteristic->setCallbacks(idCallbacks);
  pProvService->start();
  BLEDevice::getAdvertising()->addServiceUUID(PROV_SERVICE_UUID);
  BLEDevice::startAdvertising();
  Serial.println("[BLE] Mode Provisioning Aktif. Menunggu koneksi dari HP...");
}

bool provisionViaWifi(const char* registerUrl) {
  String mac = WiFi.macAddress();
  JsonDocument doc;
  doc["mac_address"] = mac.c_str();
  String payload;
  serializeJson(doc, payload);
  HTTPClient http;
  http.begin(registerUrl);
  http.addHeader("Content-Type", "application/json");
  int httpCode = http.POST(payload);
  if (httpCode == 200) {
    String response = http.getString();
    deserializeJson(doc, response);
    if (!doc["device_id"].isNull()) {
      http.end();
      return true;
    }
  }
  http.end();
  return false;
}

bool isSignalValid(int loPlusPin, int loMinusPin) {
  return (digitalRead(loPlusPin) == LOW && digitalRead(loMinusPin) == LOW);
}

String getTimestamp() {
  time_t now;
  struct tm timeinfo;
  time(&now);
  gmtime_r(&now, &timeinfo);
  if (timeinfo.tm_year < 124) {
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
    for (int i = 0; i < length; i++) {
      ecgBeatData.add(beatBuffer[i]);
    }
    JsonArray ecgAfibData = doc["ecg_afib_data"].to<JsonArray>();
    for (int i = 0; i < length; i++) {
      ecgAfibData.add(afibBuffer[i]);
    }
    String jsonString;
    serializeJson(doc, jsonString);
    HTTPClient http;
    http.begin(url);
    http.addHeader("Content-Type", "application/json");
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