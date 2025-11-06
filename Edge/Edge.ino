#include <WiFi.h>
#include <WiFiProv.h>
#include <WiFiClientSecure.h> 

#include "config.h"
#include "butterworthfilter.h"
#include "utils.h"
#include "time.h"

#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <qrcode.h>

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

extern String currentStatus; // Ambil dari Utils.cpp
int plotX = 0; // Posisi X plotter saat ini
int lastPlotY = -1; // Posisi Y sebelumnya
bool provisioningDone = false; // Flag penanda provisioning
unsigned long lastDisplayUpdate = 0;

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1); // -1 = no reset pin
QRCode qrcode;

void SysProvEvent(arduino_event_t *sys_event);
void drawQRCode(String text, int startY);
void updateOLEDPlotter(float value);
void updateOLEDStatus();

// --- FUNGSI UTAMA ARDUINO ---
void setup() {
    Serial.begin(115200);
    delay(1000);

    if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) { 
        Serial.println(F("[FATAL] Alokasi SSD1306 gagal!"));
        for(;;); // Berhenti di sini
    }
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("Booting ECG Device...");
    display.display();
     
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
        // WiFiProv.printQR(dynamicServiceName.c_str(), PROV_POP, "ble");
        
        String provPayload = "{\"ver\":\"v1\",\"name\":\"" + dynamicServiceName + "\",\"pop\":\"" + PROV_POP + "\",\"transport\":\"ble\"}";
        Serial.printf("[PROV] QR Payload: %s\n", provPayload.c_str());

        display.clearDisplay();
        display.setTextSize(1);
        display.setCursor(0,0);
        display.println("Scan QR for Wi-Fi");
        drawQRCode(provPayload, 18); 
        display.display();

        while (WiFi.status() != WL_CONNECTED) { delay(1000); }
        Serial.println("\n[PROVISIONING] Kredensial diterima! Restart untuk masuk mode operasional...");
        display.clearDisplay();
        display.setCursor(0,0);
        display.println("Wi-Fi Diterima!");
        display.println("Restarting...");
        display.display();
        delay(3000);
        ESP.restart();
        
    }
    else {
        Serial.println("\n[WIFI] WiFi Tersambung!");
        provisioningDone = true; // Tandai provisioning selesai
        currentStatus = "Connected";

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
    if (!provisioningDone) {
        // Loop kosong saat menunggu provisioning
        // (Layar OLED sudah menampilkan QR dari setup)
        return; 
    }
    
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

                    float filteredBeat = beatFilter->update(dcBlockedValue);
                    updateOLEDPlotter(filteredBeat);

                    // Serial.printf("Raw: %.0f, DC_Blocked: %.2f, Filtered: %.2f\n", rawValue, dcBlockedValue, filteredBeat);
                    Serial.println(filteredBeat);
                    
                    ecgBeatBuffer[bufferIndex] = filteredBeat;

                    bufferIndex++;
                }
            } else {
                 if (!deviceConnected) Serial.println("[WARNING] Elektroda terlepas!");
                 if (!deviceConnected) currentStatus = "Electrode Off";
            }
        }
        
        if (bufferIndex >= SIGNAL_LENGTH) {
            currentStatus = "Analyzing..."; // Update status sebelum ngirim
            updateOLEDStatus();

            String timestamp = getTimestamp();
            if (timestamp != "0000-00-00T00:00:00+07:00") { 
                String serverEndpoint = String(SERVER_ADDRESS) + "/api/analyze-ecg";
                sendDataToServer(serverEndpoint.c_str(), DEVICE_ID.c_str(), timestamp.c_str(), ecgBeatBuffer, SIGNAL_LENGTH);
            } else {
                Serial.println("[SKIP] Data tidak dikirim, waktu NTP belum sinkron.");
                currentStatus = "Time Sync Error";
            }
            bufferIndex = 0;
            beatFilter->reset();

            plotX = 0;
            lastPlotY = -1;
            display.fillRect(0, 0, SCREEN_WIDTH, PLOT_HEIGHT, SSD1306_BLACK); // Hanya bersihkan area plotter

            Serial.println("\n[INFO] Buffer direset.");
            delay(2000);
        }
    }
    
    if (isSensorActive && !deviceConnected && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
        currentStatus = "Sleeping...";
    }
    if (millis() - lastDisplayUpdate > 50) {
        lastDisplayUpdate = millis();
        updateOLEDStatus(); // Update teks status di buffer
        display.display(); // Kirim buffer ke layar
    }
}


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

void drawQRCode(String text, int startY) {
  // Buat data QR code
  uint8_t qrcodeData[qrcode_getBufferSize(3)]; // Version 3
  qrcode_init_text(&qrcode, qrcodeData, 3, ECC_LOW, text.c_str());

  int moduleSize = 2; // Ukuran tiap kotak QR (dalam pixel)
  int qrSize = qrcode.size * moduleSize;
  int xOffset = (SCREEN_WIDTH - qrSize) / 2; // Center horizontal
  int yOffset = startY;

  for (uint8_t y = 0; y < qrcode.size; y++) {
    for (uint8_t x = 0; x < qrcode.size; x++) {
      if (qrcode_getModule(&qrcode, x, y)) {
        display.fillRect(xOffset + (x * moduleSize), yOffset + (y * moduleSize), moduleSize, moduleSize, SSD1306_WHITE);
      }
    }
  }
}

void updateOLEDPlotter(float value) {
    // Mapping: Sesuaikan min/max ini dengan output sinyal filter lo
    // Ganti -500 dan 500 sesuai kebutuhan
    int y = map(value, -500, 500, PLOT_HEIGHT - 1, 0); // 0 = atas, PLOT_HEIGHT = bawah
    y = constrain(y, 0, PLOT_HEIGHT - 1); // Jaga agar tidak keluar area

    if (lastPlotY == -1) {
        // Titik pertama, jangan gambar garis
        lastPlotY = y;
    }

    // Gambar garis dari titik terakhir ke titik baru
    display.drawLine(plotX, lastPlotY, plotX + 1, y, SSD1306_WHITE);
    lastPlotY = y;
    plotX++;

    // Kalo udah mentok kanan, bersihkan area dan mulai dari kiri
    if (plotX >= SCREEN_WIDTH - 1) {
        plotX = 0;
        lastPlotY = -1;
        // Bersihkan hanya area plotter
        display.fillRect(0, 0, SCREEN_WIDTH, PLOT_HEIGHT, SSD1306_BLACK); 
    }
}

void updateOLEDStatus() {
    int statusBarY = PLOT_HEIGHT + 2; // Posisi Y di bawah plotter

    // Bersihkan area status
    display.fillRect(0, statusBarY, SCREEN_WIDTH, SCREEN_HEIGHT - statusBarY, SSD1306_BLACK);
    
    display.setTextSize(1);
    display.setCursor(0, statusBarY);
    display.print("Status: ");
    display.setTextSize(2); // Statusnya dibikin gede
    display.setCursor(0, statusBarY + 10);
    display.print(currentStatus); 
}

