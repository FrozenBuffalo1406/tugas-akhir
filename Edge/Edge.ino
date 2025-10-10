#include <WiFiManager.h>
#include <Preferences.h>
#include <ArduinoJson.h>

#include "config.h"
#include "ButterworthFilter.h"
#include "utils.h"
#include "time.h"

// --- VARIABEL GLOBAL (BUKAN KONSTANTA) ---
char SERVER_ADDRESS[100];
String DEVICE_ID = "";
bool isProvisioned = false;
Preferences preferences;
ButterworthFilter beatFilter(b_beat, a_beat, FILTER_ORDER);
ButterworthFilter afibFilter(b_afib, a_afib, FILTER_ORDER);
BLEServer* pServer = NULL;
BLECharacteristic* pEcgCharacteristic = NULL;
bool deviceConnected = false;
float ecgBeatBuffer[SIGNAL_LENGTH];
float ecgAfibBuffer[SIGNAL_LENGTH];
int bufferIndex = 0;
unsigned long lastSampleTime = 0;
const long sampleInterval = 1000 / SAMPLING_RATE;
bool isSensorActive = true;
unsigned long lastActivityTime = 0;

// --- DEFINISI CLASS CALLBACKS & FUNGSI HELPER LOKAL ---

// Deklarasi awal (Forward Declaration)
void sensorSleep();
void sensorWakeUp();

class IDCharacteristicCallback : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String value = pCharacteristic->getValue().c_str();
        if (value.length() > 0) {
            DEVICE_ID = value;
            Serial.printf("[PROVISIONING] Menerima Device ID via BLE: %s\n", DEVICE_ID.c_str());
            
            // [PERBAIKAN] Buka, Tulis, dan Tutup preferences di sini
            Preferences localPrefs;
            localPrefs.begin("ecg-device", false);
            localPrefs.putString("device_id", DEVICE_ID);
            localPrefs.end(); // Memastikan data ditulis ke flash

            isProvisioned = true;
            Serial.println("[PROVISIONING] Registrasi sukses! Me-restart perangkat dalam 3 detik...");
            delay(3000);
            ESP.restart();
        }
    }
};

class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("[BLE] Perangkat terhubung, sensor diaktifkan...");
        sensorWakeUp();
    }
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("[BLE] Perangkat terputus");
        pServer->getAdvertising()->start();
    }
};

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
        beatFilter.reset();
        afibFilter.reset();
        bufferIndex = 0;
        lastActivityTime = millis();
    }
}

// --- FUNGSI UTAMA ARDUINO ---
void setup() {
    Serial.begin(115200);
    delay(1000);

    pinMode(LO_PLUS_PIN, INPUT);
    pinMode(LO_MINUS_PIN, INPUT);
    pinMode(SDN_PIN, OUTPUT);
    digitalWrite(SDN_PIN, HIGH);

    preferences.begin("ecg-device", false);
    DEVICE_ID = preferences.getString("device_id", "");
    isProvisioned = (DEVICE_ID != "");

    WiFiManager wm;
    WiFiManagerParameter custom_server_url("server", "URL Server", DEFAULT_SERVER_ADDRESS, 100);
    wm.addParameter(&custom_server_url);

    if (isProvisioned) {
        // === JALUR OPERASIONAL NORMAL ===
        Serial.printf("[INFO] Perangkat sudah terdaftar dengan ID: %s\n", DEVICE_ID.c_str());
        if (!wm.autoConnect()) {
            Serial.println("Gagal konek, masuk mode portal.");
        }
        strcpy(SERVER_ADDRESS, custom_server_url.getValue());
        Serial.println("[WIFI] Tersambung!");

        configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
        struct tm timeinfo;
        while (!getLocalTime(&timeinfo) || timeinfo.tm_year < 124) {
            delay(1000); Serial.print(".");
        }
        Serial.println("\n[NTP] Waktu berhasil disinkronisasi!");

        setupOperationalBLE(pServer, pEcgCharacteristic, new MyServerCallbacks());
        lastActivityTime = millis();
        Serial.println("\n[INFO] Setup selesai. Memulai loop utama...");
    } else {
        // === JALUR PROVISIONING / REGISTRASI ===
        Serial.println("[PROVISIONING] Perangkat ini belum terdaftar.");
        if (wm.autoConnect("ECG-Device-Setup-AP")) {
            Serial.println("\n[WIFI] Tersambung via portal.");
            strcpy(SERVER_ADDRESS, custom_server_url.getValue());
            String registerUrl = String(SERVER_ADDRESS) + "/api/register-device";
            if (provisionViaWifi(registerUrl.c_str())) {
                Serial.println("[PROVISIONING] Registrasi via Wi-Fi berhasil! Me-restart...");
                delay(3000);
                ESP.restart();
            } else {
                startBleProvisioning(pServer, new IDCharacteristicCallback());
            }
        } else {
            Serial.println("\n[WIFI] Gagal terhubung ke Wi-Fi via portal.");
            WiFi.mode(WIFI_OFF);
            startBleProvisioning(pServer, new IDCharacteristicCallback());
        }
    }
}

void loop() {
    if (!isProvisioned) {
        delay(1000);
        return;
    }

    if (isSensorActive) {
        if (millis() - lastSampleTime >= sampleInterval) {
            lastSampleTime = millis();
            bool signalValid = isSignalValid(LO_PLUS_PIN, LO_MINUS_PIN);

            if (signalValid || deviceConnected) {
                lastActivityTime = millis();
            }

            if (signalValid) {
                if (bufferIndex < SIGNAL_LENGTH) {
                    float rawValue = analogRead(ECG_PIN);
                    float filteredBeat = beatFilter.update(rawValue);
                    float filteredAfib = afibFilter.update(rawValue);
                    
                    ecgBeatBuffer[bufferIndex] = filteredBeat;
                    ecgAfibBuffer[bufferIndex] = filteredAfib;
                    
                    if (deviceConnected) {
                        pEcgCharacteristic->setValue((uint8_t*)&filteredBeat, 4);
                        pEcgCharacteristic->notify();
                    }
                    bufferIndex++;
                }
            } else {
                if(!deviceConnected) Serial.println("[WARNING] Elektroda terlepas!");
            }
        }

        if (bufferIndex >= SIGNAL_LENGTH) {
            String timestamp = getTimestamp();
            String serverEndpoint = String(SERVER_ADDRESS) + "/api/analyze-ecg";
            sendDataToServer(serverEndpoint.c_str(), DEVICE_ID.c_str(), timestamp.c_str(), ecgBeatBuffer, ecgAfibBuffer, SIGNAL_LENGTH);
            bufferIndex = 0;
            beatFilter.reset();
            afibFilter.reset();
            Serial.println("\n[INFO] Buffer direset.");
        }
    }

    if (isSensorActive && !deviceConnected && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
    }
}