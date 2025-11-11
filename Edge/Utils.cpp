#include "utils.h"
#include "config.h"
#include <WiFi.h>
#include <ArduinoJson.h>
#include "time.h"
#include <Preferences.h>
#include "butterworthfilter.h"
#include <HTTPClient.h>
#include <WiFiClientSecure.h> 
#include "nvs_flash.h"

static const char* NTP_SERVER = "id.pool.ntp.org";
// Deklarasi eksternal
extern bool isSensorActive;
extern ButterworthFilter* beatFilter;
extern ButterworthFilter* notchFilter;
extern int bufferIndex;
extern unsigned long lastActivityTime;
extern unsigned long buttonPressStartTime;
extern bool longPressTriggered;
extern float dcBlockerW;
extern float dcBlockerX;
extern String currentStatus;
extern WiFiClientSecure client;
String currentStatus = "Initializing...";

// --- Implementasi Fungsi ---
String getDeviceIdentity() {

    String mac = WiFi.macAddress();
    JsonDocument doc;
    doc["mac_address"] = mac.c_str();
    String payload;
    serializeJson(doc, payload);
    Serial.printf("Mac Address: %s\n", mac.c_str());

    HTTPClient http;
    String registerUrl = String(SERVER_ADDRESS) + "/register-device";

    http.begin(client, registerUrl);
    http.addHeader("Content-Type", "application/json");

    int httpCode = http.POST(payload);
    if (httpCode == 200 || httpCode == 201) {
        String response = http.getString();
        Serial.printf("[WIFI-SERVER] Balasan Registrasi: %s\n", response.c_str());
        deserializeJson(doc, response);
        if (!doc["device_id"].isNull()) {
            return doc["device_id"].as<String>();
        }
    } else {
        Serial.printf("[HTTP-ERROR] Gagal registrasi, kode: %d, error: %s\n", httpCode, http.errorToString(httpCode).c_str());
    }
    return "";
}

bool isSignalValid(int loPlusPin, int loMinusPin) {
    return (digitalRead(loPlusPin) == LOW && digitalRead(loMinusPin) == LOW);
}

String getTimestamp() {
    static bool timeInitialized = false;
    struct tm timeinfo;
    

    if (!timeInitialized) {
        Serial.println("[NTP] Melakukan konfigurasi waktu (NTP) untuk pertama kali...");
        configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
        
        if (getLocalTime(&timeinfo) && timeinfo.tm_year > 125) { // Cek apakah tahun > 2023
             Serial.println("[NTP] Waktu berhasil disinkronisasi saat inisiasi!");
        } else {
            Serial.println("[NTP-WARNING] Sinkronisasi awal belum selesai, akan dicoba di background.");
        }
        timeInitialized = true;
    }
    
    if (!getLocalTime(&timeinfo)) {
        Serial.println("[TIME-ERROR] Gagal mendapatkan waktu lokal (mungkin belum sinkron).");
        return "0000-00-00T00:00:00+07:00";
    }

    char timeString[30];
    strftime(timeString, sizeof(timeString), "%Y-%m-%dT%H:%M:%S+07:00", &timeinfo);
    return String(timeString);
}

void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* beatBuffer, int length) {
    
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[WIFI-ERROR] Koneksi putus, data tidak terkirim.");
        currentStatus = "No WiFi";
        return;
    }

    JsonDocument doc;

    doc["device_id"] = deviceId;
    doc["timestamp"] = timestamp;
    JsonArray ecgBeatData = doc["ecg_beat_data"].to<JsonArray>();
    for (int i = 0; i < length; i++) { 
        ecgBeatData.add(beatBuffer[i]); 
    }

    String jsonString;
    serializeJson(doc, jsonString);

    // Cek kalo gagal (RAM masih gak cukup)
    if (jsonString.length() == 0 && doc.overflowed()) {
        Serial.println("[FATAL] Gagal alokasi JSON! RAM masih penuh.");
        currentStatus = "JSON Alloc Fail";
        return; 
    }
    HTTPClient http;
    http.begin(client, url);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(20000); // Timeout 20 detik

    Serial.println("[HTTP] Mengirim data ke server (Non-Streaming)...");

    // 4. Kirim semua sekaligus. Fungsi POST() ini MENGEMBALIKAN HTTP Code.
    int httpCode = http.POST(jsonString);

    if (httpCode > 0) {
        Serial.printf("[WIFI-SERVER] Data berhasil dikirim (Kode: %d)\n", httpCode);
        String response = http.getString();
        Serial.printf("[WIFI-SERVER] Balasan diterima: %s\n", response.c_str());

        // Parse balasan
        JsonDocument docResponse;
        DeserializationError error = deserializeJson(docResponse, response);

        if (error) {
            Serial.print(F("[JSON-ERROR] Gagal parse balasan: "));
            Serial.println(error.c_str());
            currentStatus = "Parse Error";
        } 
        // Cek "prediction"
        else if (!docResponse["prediction"].isNull()) { 
            const char* prediction = docResponse["prediction"];
            
            // --- [FIX TYPO] ---
            // 'prediction' itu udah char*, gausah pake .c_str()
            Serial.printf("[ANALYSIS] Hasil deteksi: %s\n", prediction); 
            
            currentStatus = String(prediction);
        } else {
            Serial.println("[WIFI-SERVER] Balasan OK, tapi format JSON tidak ada 'prediction'.");
            currentStatus = "No Label";
        }
        
    } else {
        Serial.print("[WIFI-ERROR] Gagal kirim data: ");
        Serial.println(http.errorToString(httpCode));
        currentStatus = "Send Error";
    }

    http.end();
    
}

void sensorSleep() {
    if (isSensorActive) {
        Serial.println("[POWER] Sensor dinonaktifkan untuk hemat daya.");
        digitalWrite(SDN_PIN, LOW);
        isSensorActive = false;
        currentStatus = "Sleeping"
    }
}

void sensorWakeUp() {
    if (!isSensorActive) {
        Serial.println("[POWER] Sensor diaktifkan.");
        digitalWrite(SDN_PIN, HIGH);
        isSensorActive = true;
        beatFilter->reset();
        notchFilter->reset();
        dcBlockerW = 0.0;
        dcBlockerX = 0.0; 
        bufferIndex = 0;
        lastActivityTime = millis();
        currentStatus = "Waking up!"
    }
}

void handleFactoryReset() {
  if (digitalRead(FACTORY_RESET_PIN) == LOW) {
    if (buttonPressStartTime == 0) {
      buttonPressStartTime = millis();
      longPressTriggered = false;
      Serial.println("[RESET] Tombol reset terdeteksi, tahan selama 5 detik...");
    }
    if (!longPressTriggered && (millis() - buttonPressStartTime > longPressDuration)) {
      Serial.println("\n[RESET] Melakukan factory reset...");
      nvs_flash_erase();
      Serial.println("[RESET] Kredensial Wi-Fi dihapus. Perangkat akan restart.");
      delay(1000);
      ESP.restart();
      longPressTriggered = true;
    } 
    else if (!longPressTriggered && (millis() - buttonPressStartTime > 2000)) { 
        sensorWakeUp();
    }
  } else {
    if (buttonPressStartTime != 0) {
      Serial.println("[RESET] Batal.");
      buttonPressStartTime = 0;
    }
  }
}


