#include <WiFi.h>
#include <WiFiProv.h>
#include <WiFiClientSecure.h> 

// #define USE_SOFT_AP
#include "config.h"
#include "butterworthfilter.h"
#include "utils.h"
#include "time.h"
#include "callbacks.h"

// --- VARIABEL GLOBAL ---
static const char* PROV_POP = "123456";
bool reset_provisioned = false;
String dynamicServiceName;
String DEVICE_ID = "";
WiFiClientSecure client;
ButterworthFilter* beatFilter = NULL;
BLEServer* pServer = NULL;
BLECharacteristic* pEcgCharacteristic = NULL;
bool deviceConnected = false;
float* ecgBeatBuffer = NULL; 

int bufferIndex = 0;
unsigned long lastSampleTime = 0;
const long sampleInterval = 1000 / SAMPLING_RATE;
bool isSensorActive = true;
unsigned long lastActivityTime = 0;
unsigned long buttonPressStartTime = 0;
bool longPressTriggered = false;
float dcBlockerW = 0.0;
float dcBlockerX = 0.0;


void SysProvEvent(arduino_event_t *sys_event) {
  switch (sys_event->event_id) {
    case ARDUINO_EVENT_WIFI_STA_GOT_IP:
      Serial.print("\nConnected IP address : ");
      Serial.println(IPAddress(sys_event->event_info.got_ip.ip_info.ip.addr));
      break;
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED: Serial.println("\nDisconnected. Connecting to the AP again... "); break;
    case ARDUINO_EVENT_PROV_START:            Serial.println("\nProvisioning started\nGive Credentials of your access point using smartphone app"); break;
    case ARDUINO_EVENT_PROV_CRED_RECV:
    {
      Serial.println("\nReceived Wi-Fi credentials");
      Serial.print("\tSSID : ");
      Serial.println((const char *)sys_event->event_info.prov_cred_recv.ssid);
      Serial.print("\tPassword : ");
      Serial.println((char const *)sys_event->event_info.prov_cred_recv.password);
      break;
    }
    case ARDUINO_EVENT_PROV_CRED_FAIL:
    {
      Serial.println("\nProvisioning failed!\nPlease reset to factory and retry provisioning\n");
      if (sys_event->event_info.prov_fail_reason == NETWORK_PROV_WIFI_STA_AUTH_ERROR) {
        Serial.println("\nWi-Fi AP password incorrect");
      } else {
        Serial.println("\nWi-Fi AP not found....Add API \" nvs_flash_erase() \" before beginProvision()");
      }
      break;
    }
    case ARDUINO_EVENT_PROV_CRED_SUCCESS: Serial.println("\nProvisioning Successful"); break;
    case ARDUINO_EVENT_PROV_END:          Serial.println("\nProvisioning Ends"); break;
    default:                              break;
  }
}
// --- FUNGSI UTAMA ARDUINO ---
void setup() {
    Serial.begin(115200);
    delay(1000);
    client.setInsecure(); 
    pinMode(FACTORY_RESET_PIN, INPUT_PULLUP);
    
    if (digitalRead(FACTORY_RESET_PIN) == LOW) {
        Serial.println("[RESET] Tombol reset terdeteksi saat boot. Tahan selama 10 detik untuk konfirmasi...");
        unsigned long pressStartTime = millis();
        bool confirmed = true;
        while (millis() - pressStartTime < 10000) {
            if (digitalRead(FACTORY_RESET_PIN) == HIGH) {Serial.println("[RESET] Batal. Tombol dilepas sebelum 10 detik.");confirmed = false;break; }
            delay(100); 
        }
        if (confirmed) {Serial.println("\n[RESET] Konfirmasi diterima. Menghapus semua kredensial Wi-Fi...");WiFi.disconnect(true, true);delay(1000);ESP.restart();}
    }
    beatFilter = new ButterworthFilter(b_beat, a_beat, FILTER_ORDER);

    pinMode(LO_PLUS_PIN, INPUT);
    pinMode(LO_MINUS_PIN, INPUT);
    pinMode(SDN_PIN, OUTPUT);
    digitalWrite(SDN_PIN, HIGH);

    ecgBeatBuffer = (float*) malloc(SIGNAL_LENGTH * sizeof(float));
    if (ecgBeatBuffer == NULL) {
        Serial.println("[FATAL] Gagal alokasi memori buffer! Program berhenti.");
        while(true);
    }
    Serial.println("[INFO] Memori buffer berhasil dialokasi.");

    WiFi.mode(WIFI_STA);
    WiFi.begin();
    WiFi.onEvent(SysProvEvent);
    
    Serial.println("Mencoba menghubungkan ke Wi-Fi...");
    int wifi_retries = 20;
    while (WiFi.status() != WL_CONNECTED && wifi_retries > 0) {
        delay(500); Serial.print("."); wifi_retries--;
    }

    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("\n[PROVISIONING] Gagal terhubung. Memulai BLE Provisioning...");

        String mac = WiFi.macAddress();
        String macSuffix = mac.substring(12); 
        macSuffix.replace(":", ""); 
        dynamicServiceName = "ECG_SETUP_" + macSuffix; 

        Serial.printf("\n[PROVISIONING] Buka aplikasi 'ESP BLE Provisioning'.\n");
        Serial.printf("[PROVISIONING] Konek ke device: %s\n", dynamicServiceName.c_str());
        Serial.printf("[PROV] Masukkan PIN (PoP): %s\n", PROV_POP);
        WiFiProv.beginProvision(
            NETWORK_PROV_SCHEME_BLE, 
            NETWORK_PROV_SCHEME_HANDLER_FREE_BLE,
            NETWORK_PROV_SECURITY_1, 
            PROV_POP, 
            dynamicServiceName.c_str()
        );
        
        Serial.printf("\n[PROVISIONING] Buka aplikasi 'ESP BLE Provisioning'.\n");
        Serial.printf("[PROVISIONING] (Mode Wi-Fi) Konek ke device: %s\n", dynamicServiceName.c_str());
        Serial.printf("[PROV] Masukkan PIN (PoP): %s\n", PROV_POP);

        Serial.println("\n[PROVISIONING] Atau scan QR ini dengan app:");
        WiFiProv.printQR(dynamicServiceName.c_str(), PROV_POP, "ble");

        while (WiFi.status() != WL_CONNECTED) { delay(1000); }
        
        Serial.println("\n[PROVISIONING] Kredensial diterima! Restart untuk masuk mode operasional...");
        delay(3000);
        ESP.restart();
    }
    else {
        Serial.println("\n[WIFI] WiFi Tersambung!");

        DEVICE_ID = getDeviceIdentity();
        if (DEVICE_ID == "") {
            Serial.println("[FATAL] Gagal mendapatkan Device ID dari server. Program berhenti.");
            while(true);
        }
        Serial.printf("[INFO] Perangkat diidentifikasi sebagai: %s\n", DEVICE_ID.c_str());

        Serial.println("[INFO] Memulai sinkronisasi waktu awal (via getTimestamp)...");
        String initialTime = getTimestamp();
        Serial.printf("[INFO] Waktu inisiasi: %s\n", initialTime.c_str());

        lastActivityTime = millis();
        Serial.println("\n[INFO] Setup selesai. Memulai loop utama...");
    }
}

void loop() {
    handleFactoryReset();
    
    if (isSensorActive) {
        if (millis() - lastSampleTime >= sampleInterval) {
            lastSampleTime = millis();
            bool signalValid = isSignalValid(LO_PLUS_PIN, LO_MINUS_PIN);
            
            if (signalValid && !isSensorActive) {
                sensorWakeUp();
            }
            if (signalValid || deviceConnected) {
                lastActivityTime = millis();
            }

            if (signalValid) {
                if (bufferIndex < SIGNAL_LENGTH) {
                    float rawValue = analogRead(ECG_PIN);

                    dcBlockerX = rawValue + (0.995 * dcBlockerW);
                    float dcBlockedValue = dcBlockerX - dcBlockerW;
                    dcBlockerW = dcBlockerX;

                    float filteredBeat = beatFilter->update(dcBlockedValue); // Filter sinyal yang sudah bersih

                    // Serial.printf("Raw: %.0f, DC_Blocked: %.2f, Filtered: %.2f\n", rawValue, dcBlockedValue, filteredBeat);
                    Serial.println(filteredBeat);
                    
                    ecgBeatBuffer[bufferIndex] = filteredBeat;

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
            if (timestamp != "0000-00-00T00:00:00+07:00") { // Cek jika waktu valid
                String serverEndpoint = String(SERVER_ADDRESS) + "/api/analyze-ecg";
                // [CLEANUP] Panggilan disederhanakan
                sendDataToServer(serverEndpoint.c_str(), DEVICE_ID.c_str(), timestamp.c_str(), ecgBeatBuffer, SIGNAL_LENGTH);
            } else {
                Serial.println("[SKIP] Data tidak dikirim, waktu NTP belum sinkron.");
            }

            bufferIndex = 0;
            beatFilter->reset();
            Serial.println("\n[INFO] Buffer direset.");
            
            delay(2000);
        }
    }
    
    if (isSensorActive && !deviceConnected && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
    }
}

