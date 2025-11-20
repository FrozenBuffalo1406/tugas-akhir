#include <WiFi.h>
#include <WiFiProv.h>
#include <WiFiClientSecure.h> 
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "config.h"
#include "butterworthfilter.h"
#include "utils.h"
#include "time.h"
#include "nvs_flash.h"
#include "qrcoderm.h"

static const char* PROV_POP = "abcd1234";
bool reset_provisioned = false;
String dynamicServiceName;
String DEVICE_ID = "";
WiFiClientSecure client;
ButterworthFilter* beatFilter = NULL;
ButterworthFilter* notchFilter = NULL;
float* ecgBeatBuffer = NULL; 

int bufferIndex = 0;
unsigned long lastSampleTime = 0;
const long sampleInterval = 1000 / SAMPLING_RATE;
bool isSensorActive = true;
unsigned long lastActivityTime = 0;
unsigned long buttonPressStartTime = 0;
bool longPressTriggered = false;
bool mediumPressTriggered = false;
bool buttonWasPressed = false;
bool isQrCodeActive = false;
float dcBlockerW = 0.0;
float dcBlockerX = 0.0;
float emaFilteredValue = 0.0;

extern String currentStatus; // Ambil dari Utils.cpp
int plotX = 0; // Posisi X plotter saat ini
int lastPlotY = -1; // Posisi Y sebelumnya
bool provisioningDone = false; // Flag penanda provisioning
unsigned long lastDisplayUpdate = 0;

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);
QRCode qrcode;


void drawQRCode(String text, int startY);
void updateOLEDPlotter(float value);
void updateOLEDStatus();

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
        Serial.println("[RESET] Tombol reset terdeteksi saat boot. Tahan selama 5 detik untuk konfirmasi...");
        unsigned long pressStartTime = millis();
        bool confirmed = true;
        while (millis() - pressStartTime < LONG_PRESS_DURATION_MS) {
            if (digitalRead(FACTORY_RESET_PIN) == HIGH) {Serial.println("[RESET] Batal. Tombol dilepas sebelum 10 detik.");confirmed = false;break; }
            delay(100); 
        }
        if (confirmed) {
            Serial.println("\n[RESET] Konfirmasi diterima. Menghapus semua kredensial Wi-Fi...");
            nvs_flash_erase();
            display.clearDisplay();
            display.setTextSize(1);
            display.setTextColor(SSD1306_BLACK);
            display.setCursor(0, 0);
            display.println("Resetting ECG Device...");
            display.display();
            delay(1000);
            ESP.restart();
        }
    }
    
    beatFilter = new ButterworthFilter(b_beat, a_beat, FILTER_ORDER);
    notchFilter = new ButterworthFilter(b_notch, a_notch, NOTCH_FILTER_ORDER);

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

        Serial.println("[RADIO] Mematikan Wi-Fi (STA)");
        WiFi.mode(WIFI_MODE_NULL);

        Serial.printf("\n[PROVISIONING] Buka aplikasi 'ESP BLE Provisioning'.\n");
        Serial.printf("[PROVISIONING] Konek ke device: %s\n", dynamicServiceName.c_str());
        Serial.printf("[PROVISIONING] mac address: %s\n", mac.c_str());
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
        
        String provPayload = "{\"ver\":\"v1\",\"name\":\"" + dynamicServiceName + "\",\"pop\":\"" + PROV_POP + "\",\"transport\":\"ble\"}";
        Serial.printf("[PROV] QR Payload: %s\n", provPayload.c_str());

        display.clearDisplay();
        // 2. Gambar QR Code warna HITAM
        drawQRCode(provPayload, -1);
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

        display.clearDisplay();
        display.fillRect(0, 0, SCREEN_WIDTH, PLOT_HEIGHT, SSD1306_BLACK); 
        display.display();
        
        // Tampilkan IP biar keren
        String ip = WiFi.localIP().toString();
        Serial.println("[WIFI] IP: " + ip);
        display.println(ip);
        
        display.display();
        delay(2000); // Kasih jeda biar kebaca
        display.clearDisplay();

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
    handleMultiFunctionButton();
    if (!provisioningDone) {
        return; 
    }
    if (!isQrCodeActive) { 
        if (millis() - lastDisplayUpdate > 50) {
            lastDisplayUpdate = millis();
            updateOLEDStatus(); 
            display.display(); 
        }
    }

    if (millis() - lastDisplayUpdate > 50) {
        lastDisplayUpdate = millis();
        updateOLEDStatus(); // Update teks status di buffer
        display.display(); // Kirim buffer ke layar
    }
    
    if (isSensorActive && !isQrCodeActive) {
        if (millis() - lastSampleTime >= sampleInterval) {
            lastSampleTime = millis();
            bool signalValid = isSignalValid(LO_PLUS_PIN, LO_MINUS_PIN);
            
            if (signalValid && !isSensorActive) {
                sensorWakeUp();
            }
            if (signalValid) {
                lastActivityTime = millis();
            }

            if (signalValid) {
                if (bufferIndex < SIGNAL_LENGTH) {
                    float rawValue = analogRead(ECG_PIN);

                    dcBlockerX = rawValue + (0.995 * dcBlockerW);
                    float dcBlockedValue = dcBlockerX - dcBlockerW;
                    dcBlockerW = dcBlockerX;

                    float notchedValue = notchFilter->update(dcBlockedValue);
                    float filteredBeat = beatFilter->update(notchedValue);

                    if (bufferIndex == 0) {
                        // Kalo data pertama, samain aja
                        emaFilteredValue = filteredBeat; 
                    } else {
                        // Kalo data selanjutnya, pake rumus EMA
                        emaFilteredValue = (EMA_ALPHA * filteredBeat) + ((1.0 - EMA_ALPHA) * emaFilteredValue);
                    }
                    Serial.println(emaFilteredValue); // Kita log yang alus
                    ecgBeatBuffer[bufferIndex] = emaFilteredValue; // Simpen yang alus

                    updateOLEDPlotter(emaFilteredValue);
                    // Serial.println(rawValue);
                    // ecgBeatBuffer[bufferIndex] = filteredBeat;
                    bufferIndex++;
                }
            } else {
                if(!signalValid) {
                Serial.println("[WARNING] Elektroda terlepas!");
                currentStatus = "Electrode Off";
                }
                if (bufferIndex < SIGNAL_LENGTH) {
                    updateOLEDPlotter(0.0); // Gambar garis lurus (nilai 0)
                    ecgBeatBuffer[bufferIndex] = 0.0; // Isi buffer dgn 0
                    emaFilteredValue = 0.0;
                    bufferIndex++;
                }
            }
        }
        
        if (bufferIndex >= SIGNAL_LENGTH) {
            currentStatus = "Analyzing..."; // Update status sebelum ngirim
            if (!isQrCodeActive) updateOLEDStatus();

            String timestamp = getTimestamp();
            if (timestamp != "0000-00-00T00:00:00+07:00") { 
                String serverEndpoint = String(SERVER_ADDRESS) + "/analyze-ecg";
                sendDataToServer(serverEndpoint.c_str(), DEVICE_ID.c_str(), timestamp.c_str(), ecgBeatBuffer, SIGNAL_LENGTH);
            } else {
                Serial.println("[SKIP] Data tidak dikirim, waktu NTP belum sinkron.");
                currentStatus = "Time Sync Error";
            }
            bufferIndex = 0;
            beatFilter->reset();
            notchFilter->reset();
            emaFilteredValue = 0.0;

            plotX = 0;
            lastPlotY = -1;
            if (!isQrCodeActive) display.fillRect(0, 0, SCREEN_WIDTH, PLOT_HEIGHT, SSD1306_BLACK);
            delay(1000);
        }
    }
    
    if (isSensorActive && (millis() - lastActivityTime > INACTIVITY_TIMEOUT_MS)) {
        sensorSleep();
    }
}

// [FILE: Edge.ino]

// Hapus parameter warna, kita bikin standar OLED (Black on White) biar tajam
void drawQRCode(String text, int startY) {
    // 1. Setup QR Code (Versi 3)
    uint8_t qrcodeData[qrcode_getBufferSize(3)];
    qrcode_initText(&qrcode, qrcodeData, 3, ECC_LOW, text.c_str());

    int moduleSize = 2; // 1 titik = 2x2 pixel
    int qrRawSize = qrcode.size * moduleSize; // Ukuran mentah QR (misal 58px)
    int padding = 2; // Margin putih di sekeliling QR (Quiet Zone)
    
    // Hitung posisi X biar tengah
    int xOffset = (SCREEN_WIDTH - qrRawSize) / 2;
    
    // Hitung posisi Y (bisa custom startY, atau otomatis tengah kalo startY -1)
    int yOffset = startY;
    if (startY == -1) {
        yOffset = (SCREEN_HEIGHT - qrRawSize) / 2;
    }

    // 2. [LOGIKA BARU] Gambar KOTAK PUTIH sebagai Background (Padding Kontras)
    // Kita gambar kotak sedikit lebih besar dari QR-nya
    int bgX = xOffset - padding;
    int bgY = yOffset - padding;
    int bgW = qrRawSize + (padding * 2);
    int bgH = qrRawSize + (padding * 2);
    
    // Pastikan gak gambar di luar layar (Clamping)
    if (bgX < 0) bgX = 0;
    if (bgY < 0) bgY = 0;
    if (bgW > SCREEN_WIDTH) bgW = SCREEN_WIDTH;
    if (bgH > SCREEN_HEIGHT) bgH = SCREEN_HEIGHT;

    display.fillRect(bgX, bgY, bgW, bgH, SSD1306_WHITE); // Background PUTIH

    // 3. Gambar Modul QR Code (HITAM)
    for (uint8_t y = 0; y < qrcode.size; y++) {
        for (uint8_t x = 0; x < qrcode.size; x++) {
            if (qrcode_getModule(&qrcode, x, y)) {
                display.fillRect(
                    xOffset + (x * moduleSize), 
                    yOffset + (y * moduleSize), 
                    moduleSize, 
                    moduleSize, 
                    SSD1306_BLACK // Modul HITAM
                );
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
    int statusBarHeight = 16;
    
    display.fillRect(0, statusBarY, SCREEN_WIDTH, statusBarHeight, SSD1306_BLACK);
    display.setTextSize(1);
    display.setCursor(0, statusBarY); // Taruh kursor di kiri
    String statusText = "Status:" + currentStatus;
    if (statusText.length() > 17) { 
        statusText = statusText.substring(0, 17) + "..";
    }
    
    display.print(statusText);
}

