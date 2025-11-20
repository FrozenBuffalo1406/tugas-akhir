#include <WiFi.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Adafruit_SSD1306.h>

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
extern bool isQrCodeActive;
extern String DEVICE_ID;
extern float dcBlockerW;
extern float dcBlockerX;
extern String currentStatus;
extern WiFiClientSecure client;
extern Adafruit_SSD1306 display;
String currentStatus = "Initializing...";

void drawQRCode(String text, int startY);

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

void showDeviceInfoQR() {
    Serial.println("[QR] Menampilkan Info Perangkat via QR Code...");
    
    String mac = WiFi.macAddress();
    
    JsonDocument doc;
    doc["device_id"] = DEVICE_ID;
    doc["mac_address"] = mac.c_str();
    String payload;
    serializeJson(doc, payload);

    Serial.printf("[QR] Payload: %s\n", payload.c_str()); // Debug
    
    // --- HITUNG UKURAN ---
    // Kita paksa visualisasi seolah-olah Version 3 (29 modul)
    // Ditambah margin (quiet zone) misal 2 modul kiri-kanan-atas-bawah
    // Total butuh: (29 + 4) * 2 pixel = 66 pixel. 
    // Karena layar cuma 64px, margin kita tipisin jadi 1 modul atau 0 (QR mentok atas bawah)
    
    int moduleSize = 2;
    int qrRawSize = 29 * moduleSize; // 58 pixel
    int bgSize = 64; // Tinggi kotak background (full height layar)
    int xBgOffset = (SCREEN_WIDTH - bgSize) / 2; // Posisi X kotak background biar tengah

    display.clearDisplay(); // Layar dasar hitam
    display.fillRect(xBgOffset, 0, bgSize, bgSize, SSD1306_WHITE);

    // 2. Gambar QR Code HITAM di atas kotak putih tadi
    // Warna QR = SSD1306_BLACK
    drawQRCode(payload, (SCREEN_HEIGHT - 58) / 2, SSD1306_BLACK);
    
    display.display();
}

void handleMultiFunctionButton() {
  // --- TOMBOL SEDANG DITEKAN (LOW) ---
  if (digitalRead(FACTORY_RESET_PIN) == LOW) {

    if (buttonPressStartTime == 0) {
      Serial.println("[BTN] Tombol terdeteksi...");
      buttonPressStartTime = millis();
      longPressTriggered = false;
      mediumPressTriggered = false;
      buttonWasPressed = true;
    }

    unsigned long pressDuration = millis() - buttonPressStartTime;

    // --- [LOGIKA BARU] Tahan 3 Detik -> LANGSUNG AKTIFKAN QR ---
    if (pressDuration > MEDIUM_PRESS_DURATION_MS && !mediumPressTriggered && !longPressTriggered) {
      Serial.println("\n[BTN] Tahanan 3 detik tercapai. Mengaktifkan QR Code...");
      
      // Langsung eksekusi di sini (gak nunggu lepas)
      showDeviceInfoQR(); 
      isQrCodeActive = true; 
      currentStatus = "QR Active";
      
      mediumPressTriggered = true; // Kunci biar gak dieksekusi ulang
    }

    // --- Cek Tahan 7 Detik (Long Press) -> RESET ---
    if (!longPressTriggered && pressDuration > LONG_PRESS_DURATION_MS) {
      Serial.println("\n[BTN] Tahanan 7 detik terdeteksi. Melakukan factory reset...");
      
      nvs_flash_erase();
      Serial.println("[RESET] Kredensial Wi-Fi dihapus. Perangkat akan restart.");
      
      // Kasih feedback visual dikit sebelum mati
      display.clearDisplay();
      display.setCursor(0,0);
      display.setTextColor(SSD1306_WHITE);
      display.println("RESETTING...");
      display.display();

      delay(1000);
      ESP.restart();
      longPressTriggered = true;
    }

  } 
  // --- TOMBOL DILEPAS (HIGH) ---
  else {
    if (buttonWasPressed) {
      // Kita cek ini lepasnya gara-gara apa?
      if (longPressTriggered) {
        sensorWakeUp();
      } 
      else if (mediumPressTriggered) {
        // Ini berarti user baru aja selesai nampilin QR Code (tahan 3 detik terus lepas).
        // Jangan ngapa-ngapain, biarin QR-nya tetep tampil.
        Serial.println("[BTN] Lepas tombol (Selesai aktifin QR).");
        display.clearDisplay();
      } 
      else {
        if (isQrCodeActive) {
          // Kalo QR lagi nyala, klik ini buat BALIK NORMAL
          Serial.println("[BTN] Klik cepat. Menutup QR Code...");
          isQrCodeActive = false;
          
          // Balikin layar ke mode normal (hitam)
          display.clearDisplay();
          display.fillRect(0, 0, SCREEN_WIDTH, PLOT_HEIGHT, SSD1306_BLACK);
          
          sensorWakeUp(); 
          currentStatus = "Waking Up";

        } else {
          // Kalo QR lagi mati, klik ini buat WAKEUP SENSOR biasa
          Serial.println("[BTN] Klik cepat. Sensor wakeup...");
          sensorWakeUp();
          currentStatus = "Waking Up";
        }
      }
    }
    
    // Reset semua flag
    buttonPressStartTime = 0;
    buttonWasPressed = false;
    mediumPressTriggered = false; 
    longPressTriggered = false;
  }
}


