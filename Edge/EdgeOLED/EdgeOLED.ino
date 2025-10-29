#include <WiFi.h>
#include <WiFiProv.h>
#include "config.h"
#include "ButterworthFilter.h"
#include "utils.h"
#include "time.h"
#include "Callbacks.h"
#include "Display.h"
#include "SignalProcessing.h"

// --- VARIABEL GLOBAL ---
String DEVICE_ID = "";
ButterworthFilter* beatFilter = NULL;
ButterworthFilter* afibFilter = NULL;
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
unsigned long buttonPressStartTime = 0;
bool longPressTriggered = false;

// Variabel untuk data hasil analisis
int bpm = 0;
float hrv = 0.0;
String predictionLabel = "Normal";

// --- FUNGSI UTAMA ARDUINO ---
void setup() {
    Serial.begin(115200);
    delay(1000);

    setupDisplay(); // Inisialisasi layar

    beatFilter = new ButterworthFilter(b_beat, a_beat, FILTER_ORDER);
    afibFilter = new ButterworthFilter(b_afib, a_afib, FILTER_ORDER);

    pinMode(FACTORY_RESET_PIN, INPUT_PULLUP);
    pinMode(LO_PLUS_PIN, INPUT);
    pinMode(LO_MINUS_PIN, INPUT);
    pinMode(SDN_PIN, OUTPUT);
    digitalWrite(SDN_PIN, HIGH);

    if (digitalRead(FACTORY_RESET_PIN) == LOW) {
        displayMessage("Resetting...");
        Serial.println("[RESET] Menghapus kredensial Wi-Fi...");
        WiFi.disconnect(true, true);
        delay(1000);
        ESP.restart();
    }

    WiFi.mode(WIFI_STA);
    WiFi.begin();
    
    displayMessage("Connecting WiFi...");
    Serial.println("Mencoba menghubungkan ke Wi-Fi...");
    int wifi_retries = 20;
    while (WiFi.status() != WL_CONNECTED && wifi_retries > 0) {
        delay(500); Serial.print("."); wifi_retries--;
    }

    if (WiFi.status() != WL_CONNECTED) {
        displayMessage("Setup WiFi via BLE");
        Serial.println("\n[PROVISIONING] Memulai BLE Provisioning...");
        WiFiProv.beginProvision(NETWORK_PROV_SCHEME_BLE, NETWORK_PROV_SCHEME_HANDLER_FREE_BT, NETWORK_PROV_SECURITY_1, "123456", "ECG_Setup");
        Serial.println("\n[PROV] PIN: 123456");
        while (WiFi.status() != WL_CONNECTED) { delay(1000); }
        Serial.println("\n[PROV] Kredensial diterima! Restart...");
        displayMessage("Restarting...");
        delay(3000);
        ESP.restart();
    }
    
    Serial.println("\n[WIFI] WiFi Tersambung!");
    displayMessage("Connected!\nIP: " + WiFi.localIP().toString());
    
    DEVICE_ID = getDeviceIdentity();
    if (DEVICE_ID == "") {
        Serial.println("[FATAL] Gagal dapat ID dari server.");
        displayMessage("Server Error!\nCheck console.");
        while(true);
    }
    Serial.printf("[INFO] Device ID: %s\n", DEVICE_ID.c_str());

    configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
    struct tm timeinfo;
    int ntp_retries = 30;
    while ((!getLocalTime(&timeinfo) || timeinfo.tm_year < 124) && ntp_retries > 0) {
        delay(1000);
        Serial.print(".");
        ntp_retries--;
    }
    if (ntp_retries > 0) Serial.println("\n[NTP] Waktu berhasil disinkronisasi!");
    else Serial.println("\n[NTP-WARNING] Gagal sinkronisasi waktu (Timeout).");
    
    setupOperationalBLE(pServer, pEcgCharacteristic, new MyServerCallbacks());
    lastActivityTime = millis();
    Serial.println("\n[INFO] Setup selesai. Memulai loop utama...");
}

void loop() {
    handleFactoryReset();
    
    float latestFilteredValue = 0;

    if (isSensorActive) {
        if (millis() - lastSampleTime >= sampleInterval) {
            lastSampleTime = millis();
            bool signalValid = isSignalValid(LO_PLUS_PIN, LO_MINUS_PIN);
            
            if (signalValid || deviceConnected) { lastActivityTime = millis(); }

            if (signalValid) {
                if (bufferIndex < SIGNAL_LENGTH) {
                    float rawValue = analogRead(ECG_PIN);
                    latestFilteredValue = beatFilter->update(rawValue);
                    float filteredAfib = afibFilter->update(rawValue);
                    ecgBeatBuffer[bufferIndex] = latestFilteredValue;
                    ecgAfibBuffer[bufferIndex] = filteredAfib;
                    if (deviceConnected) {
                        pEcgCharacteristic->setValue((uint8_t*)&latestFilteredValue, 4);
                        pEcgCharacteristic->notify();
                    }
                    bufferIndex++;
                }
            }
        }
        
        if (bufferIndex >= SIGNAL_LENGTH) {
            // Analisis data yang sudah terkumpul
            processECG(ecgBeatBuffer, SIGNAL_LENGTH, SAMPLING_RATE, bpm, hrv);
            Serial.printf("[ANALYSIS] BPM: %d, HRV: %.2f\n", bpm, hrv);
            
            String timestamp = getTimestamp();
            String serverEndpoint = String(SERVER_ADDRESS) + "/api/analyze-ecg";
            sendDataToServer(serverEndpoint.c_str(), DEVICE_ID.c_str(), timestamp.c_str(), ecgBeatBuffer, ecgAfibBuffer, SIGNAL_LENGTH);
            
            bufferIndex = 0;
            beatFilter->reset();
            afibFilter->reset();
            Serial.println("\n[INFO] Buffer direset.");
            delay(100);
        }
    }
    
    // Update display secara kontinyu
    updateDisplay(bpm, hrv, predictionLabel, latestFilteredValue);
    
    if (isSensorActive && !deviceConnected && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
    }
}

