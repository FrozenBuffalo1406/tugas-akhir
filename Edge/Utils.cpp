#include <WiFi.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Adafruit_SSD1306.h>

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>

#include "utils.h"
#include "config.h"
#include "time.h"
#include "butterworthfilter.h"
#include "nvs_flash.h"

static const char* NTP_SERVER = "id.pool.ntp.org";
extern bool isSensorActive;
extern ButterworthFilter* beatFilter;
extern ButterworthFilter* notchFilter;
extern int bufferIndex;
extern unsigned long lastActivityTime;
extern unsigned long buttonPressStartTime;
extern bool longPressTriggered;
extern bool mediumPressTriggered;
extern bool buttonWasPressed;

extern bool isBleActive;

extern String DEVICE_ID;
extern float dcBlockerW;
extern float dcBlockerX;
extern String currentStatus;
extern WiFiClientSecure client;
extern Adafruit_SSD1306 display;
String currentStatus = "Initializing...";

extern void updateOLEDboot(String value);

void sensorSleep() {
    if (isSensorActive) {
        Serial.println("[POWER] Sensor dinonaktifkan untuk hemat daya.");
        digitalWrite(SDN_PIN, LOW);
        isSensorActive = false;
        currentStatus = "Sleeping";
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
        currentStatus = "Waking up!";
    }
}

void startBLEPairingService() {
    Serial.println("[BLE] Memulai Mode Pairing...");

    String tempMac = WiFi.macAddress();
    
    // Jaga-jaga kalo stringnya kosong (jarang terjadi, tapi safety first)
    if (tempMac == "" || tempMac == "00:00:00:00:00:00") {
        WiFi.mode(WIFI_STA); // Hidupin bentar
        tempMac = WiFi.macAddress();
    }
    
    // Matikan WiFi sementara biar stabil
    WiFi.mode(WIFI_MODE_NULL);

    // Nama Device Unik (ECG-XXXXXX)
    String bleName = "ECG-" + String((uint32_t)ESP.getEfuseMac(), HEX); 
    BLEDevice::init(bleName.c_str());
    
    BLEServer *pServer = BLEDevice::createServer();
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    // Karakteristik 1: Device ID
    BLECharacteristic *pCharID = pService->createCharacteristic(
                                    CHAR_DEVICE_ID_UUID,
                                    BLECharacteristic::PROPERTY_READ
                                );
    pCharID->setValue(DEVICE_ID.c_str());
    
    // Karakteristik 2: MAC Address
    BLECharacteristic *pCharMAC = pService->createCharacteristic(
                                    CHAR_MAC_UUID,
                                    BLECharacteristic::PROPERTY_READ
                                );
    pCharMAC->setValue(tempMac.c_str());
    
    pService->start();
    
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);  
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
    
    Serial.println("[BLE] Advertising started.");
    
    // Update Layar OLED
    display.clearDisplay();
    display.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, SSD1306_BLACK);
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    
    display.setCursor(0, 10);
    display.println(">> BLUETOOTH MODE <<");
    display.setCursor(0, 30);
    display.println("Nama: " + bleName);
    display.setCursor(0, 50);
    display.println("Ready to Pair...");
    display.display();
}

void stopBLEPairingService() {
    Serial.println("[BLE] Menghentikan BLE...");
    BLEDevice::deinit(true); // Matikan stack BLE
    isBleActive = false;
    
    display.clearDisplay();
    display.setCursor(0, 0);
    display.setTextColor(SSD1306_WHITE);
    display.println("BLE Stopped.");
    display.println("Restarting WiFi...");
    display.display();
    
    delay(1000);
    ESP.restart(); // Restart adalah cara paling bersih balik ke WiFi
}

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
    struct tm timeinfo;
    static bool configSent = false;

    // 1. Config Time Cuma SEKALI, tapi pake 3 Server Backup
    if (!configSent) {
        Serial.println("[NTP] Mengkonfigurasi waktu dengan 3 server...");
        // Prioritas: 1. Server Indo, 2. Server Global, 3. Server Google (Paling stabil)
        configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, "id.pool.ntp.org", "pool.ntp.org", "time.google.com");
        configSent = true;
    }

    if (!getLocalTime(&timeinfo, 5000)) { 
        Serial.println("[TIME-ERROR] Gagal sync waktu (Timeout). Mencoba sekali lagi...");
        
        // Retry mechanism: Coba lagi sekali lagi dng timeout 3 detik
        if (!getLocalTime(&timeinfo, 3000)) {
            Serial.println("[TIME-ERROR] Masih gagal. Pake waktu default.");
            return "0000-00-00T00:00:00+07:00"; // Return default biar program gak crash
        }
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

    int httpCode = http.POST(jsonString);

    if (httpCode > 0) {
        Serial.printf("[WIFI-SERVER] Data berhasil dikirim (Kode: %d)\n", httpCode);
        String response = http.getString();
        Serial.printf("[WIFI-SERVER] Balasan diterima: %s\n", response.c_str());

        JsonDocument docResponse;
        DeserializationError error = deserializeJson(docResponse, response);

        if (error) {
            Serial.print(F("[JSON-ERROR] Gagal parse balasan: "));
            Serial.println(error.c_str());
            currentStatus = "Parse Error";
        } 
        else if (!docResponse["prediction"].isNull()) { 
            const char* prediction = docResponse["prediction"];
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

void handleMultiFunctionButton() {
  // --- TOMBOL DITEKAN (LOW) ---
  if (digitalRead(FACTORY_RESET_PIN) == LOW) {

    if (buttonPressStartTime == 0) {
      Serial.println("[BTN] Tombol terdeteksi...");
      buttonPressStartTime = millis();
      longPressTriggered = false;
      mediumPressTriggered = false;
      buttonWasPressed = true;
    }

    unsigned long pressDuration = millis() - buttonPressStartTime;

    // --- Tahan 3 Detik -> BLE ---
    if (pressDuration > MEDIUM_PRESS_DURATION_MS && !mediumPressTriggered && !longPressTriggered) {
        Serial.println("\n[BTN] Tahanan 3 detik. Aktifkan BLE.");
        
        if (!isBleActive) {
            isBleActive = true;
            currentStatus = "BLE Pairing";
            startBLEPairingService();
        }
        mediumPressTriggered = true;
    }

    // --- Tahan 7 Detik -> RESET ---
    if (!longPressTriggered && pressDuration > LONG_PRESS_DURATION_MS) {
      Serial.println("\n[BTN] Tahanan 7 detik. Factory Reset.");
      
      nvs_flash_erase();
      updateOLEDboot("RESETTING DEVICE...");
      
      delay(1000);
      ESP.restart();
      longPressTriggered = true;
    }

  } 
  // --- TOMBOL DILEPAS (HIGH) ---
  else {
    if (buttonWasPressed) {
      if (longPressTriggered) { } 
      else if (mediumPressTriggered) {
        // Tombol dilepas setelah BLE nyala. Biarin aja.
      } 
      else {
        // --- KLIK CEPAT (SHORT PRESS) ---
        if (isBleActive) {
            // Kalo BLE nyala, klik buat matiin
            Serial.println("[BTN] Klik: Matikan BLE.");
            stopBLEPairingService();
        } else {
            // Kalo Normal, klik buat wakeup sensor
            Serial.println("[BTN] Klik: Wakeup Sensor.");
            sensorWakeUp();
            currentStatus = "Waking Up";
        }
      }
    }
    
    buttonPressStartTime = 0;
    buttonWasPressed = false;
    mediumPressTriggered = false; 
    longPressTriggered = false;
  }
}




