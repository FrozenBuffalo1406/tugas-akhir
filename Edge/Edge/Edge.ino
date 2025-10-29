#include <WiFi.h>
#include <WiFiProv.h>

#include "config.h"
#include "butterworthfilter.h"
#include "utils.h"
#include "time.h"
#include "callbacks.h"

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

// --- FUNGSI UTAMA ARDUINO ---
void setup() {
    Serial.begin(115200);
    delay(1000);

    // Inisialisasi pin tombol reset DULUAN
    pinMode(FACTORY_RESET_PIN, INPUT_PULLUP);
    
    // Logika baru untuk reset yang harus ditahan 10 detik
    if (digitalRead(FACTORY_RESET_PIN) == LOW) {
        Serial.println("[RESET] Tombol reset terdeteksi saat boot. Tahan selama 10 detik untuk konfirmasi...");
        unsigned long pressStartTime = millis();
        bool confirmed = true;
        
        while (millis() - pressStartTime < 10000) {
            if (digitalRead(FACTORY_RESET_PIN) == HIGH) {
                Serial.println("[RESET] Batal. Tombol dilepas sebelum 10 detik.");
                confirmed = false;
                break; 
            }
            delay(100); 
        }

        if (confirmed) {
            Serial.println("\n[RESET] Konfirmasi diterima. Menghapus semua kredensial Wi-Fi...");
            WiFi.disconnect(true, true);
            delay(1000);
            ESP.restart();
        }
    }

    beatFilter = new ButterworthFilter(b_beat, a_beat, FILTER_ORDER);
    afibFilter = new ButterworthFilter(b_afib, a_afib, FILTER_ORDER);

    pinMode(LO_PLUS_PIN, INPUT);
    pinMode(LO_MINUS_PIN, INPUT);
    pinMode(SDN_PIN, OUTPUT);
    digitalWrite(SDN_PIN, HIGH);

    WiFi.mode(WIFI_STA);
    WiFi.begin();
    
    Serial.println("Mencoba menghubungkan ke Wi-Fi...");
    int wifi_retries = 20;
    while (WiFi.status() != WL_CONNECTED && wifi_retries > 0) {
        delay(500); Serial.print("."); wifi_retries--;
    }

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("\n[PROVISIONING] Gagal terhubung. Memulai BLE Provisioning...");
        WiFiProv.beginProvision(NETWORK_PROV_SCHEME_BLE, NETWORK_PROV_SCHEME_HANDLER_FREE_BT, NETWORK_PROV_SECURITY_1, "123456", "ECG_Setup");
        Serial.println("\n[PROVISIONING] PIN (Proof of Possession): 123456");
        Serial.println("Buka aplikasi 'ESP BLE Provisioning', konek ke 'ECG_Setup', dan masukkan PIN.");
        while (WiFi.status() != WL_CONNECTED) { delay(1000); }
        Serial.println("\n[PROVISIONING] Kredensial diterima! Restart...");
        delay(3000);
        ESP.restart();
    }
    
    Serial.println("\n[WIFI] WiFi Tersambung!");
    Serial.print("Alamat IP: ");
    Serial.println(WiFi.localIP());

    DEVICE_ID = getDeviceIdentity();
    if (DEVICE_ID == "") {
        Serial.println("[FATAL] Gagal mendapatkan Device ID dari server. Program berhenti.");
        while(true);
    }
    Serial.printf("[INFO] Perangkat diidentifikasi sebagai: %s\n", DEVICE_ID.c_str());

    configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER);
    struct tm timeinfo;
    int ntp_retries = 30;
    while ((!getLocalTime(&timeinfo) || timeinfo.tm_year < 124) && ntp_retries > 0) {
        delay(1000); Serial.print("."); ntp_retries--;
    }
    if(ntp_retries > 0) Serial.println("\n[NTP] Waktu berhasil disinkronisasi!");
    else Serial.println("\n[NTP-WARNING] Gagal sinkronisasi waktu (Timeout).");

    setupOperationalBLE(pServer, pEcgCharacteristic, new MyServerCallbacks());
    lastActivityTime = millis();
    Serial.println("\n[INFO] Setup selesai. Memulai loop utama...");
}

void loop() {
    handleFactoryReset();
    
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
                    float filteredBeat = beatFilter->update(rawValue);
                    float filteredAfib = afibFilter->update(rawValue);
                    
                    // [PERBAIKAN] Kode untuk print data sudah ditambahkan kembali
                    Serial.printf("Raw: %.0f, Filtered: %.2f\n", rawValue, filteredBeat);

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
            beatFilter->reset();
            afibFilter->reset();
            Serial.println("\n[INFO] Buffer direset.");
            
            delay(2000); // Beri server waktu untuk "napas"
        }
    }
    
    if (isSensorActive && !deviceConnected && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
    }
}

