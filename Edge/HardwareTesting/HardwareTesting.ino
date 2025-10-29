#include <WiFi.h>
#include <HTTPClient.h>

// =================================================================
// ==                 PILIH MODE TES DI SINI                      ==
// =================================================================
// Hapus tanda '//' di salah satu baris di bawah ini untuk memilih tes.
// Pastikan hanya SATU tes yang aktif dalam satu waktu.

// #define TEST_WIFI_SCAN         // Tes 1: Cek apakah modul Wi-Fi bisa 'melihat' jaringan sekitar.
// #define TEST_WIFI_CONNECT      // Tes 2: Cek apakah ESP32 bisa konek ke Wi-Fi lu.
// #define TEST_HTTP_REQUEST      // Tes 3: Cek apakah ESP32 bisa 'ngobrol' dengan internet.
#define TEST_ANALOG_PIN_34     // Tes 4: Cek apakah pin ADC untuk EKG berfungsi.
//#define TEST_DIGITAL_PINS      // Tes 5: Cek apakah pin LO+/LO- dan SDN berfungsi.

// --- KONFIGURASI UNTUK TES ---
const char* WIFI_SSID = "OIOI SPACE"; // Ganti dengan nama Wi-Fi lu
const char* WIFI_PASS = "password_wifi_lu"; // GANTI DENGAN PASSWORD ASLI

void setup() {
  Serial.begin(115200);
  delay(2000);
  Serial.println("\n\n--- MEMULAI HARDWARE TEST BENCH ---");

#ifdef TEST_WIFI_SCAN
  Serial.println(">>> MODE TES 1: WiFi Scan <<<");
  Serial.println("Mencari jaringan Wi-Fi di sekitar...");
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);
  int n = WiFi.scanNetworks();
  if (n == 0) {
      Serial.println("--> HASIL: GAGAL! Tidak ada jaringan Wi-Fi yang terdeteksi. Kemungkinan antena atau modul Wi-Fi rusak.");
  } else {
      Serial.print("--> HASIL: SUKSES! Ditemukan ");
      Serial.print(n);
      Serial.println(" jaringan:");
      for (int i = 0; i < n; ++i) {
        Serial.printf("    %d: %s (%d)\n", i + 1, WiFi.SSID(i).c_str(), WiFi.RSSI(i));
      }
  }
#endif

#ifdef TEST_WIFI_CONNECT
  Serial.println(">>> MODE TES 2: WiFi Connect <<<");
  Serial.print("Mencoba konek ke: ");
  Serial.println(WIFI_SSID);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  int retries = 20;
  while (WiFi.status() != WL_CONNECTED && retries > 0) {
    delay(500);
    Serial.print(".");
    retries--;
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n--> HASIL: SUKSES! WiFi terhubung.");
    Serial.print("    Alamat IP: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("\n--> HASIL: GAGAL! Tidak bisa terhubung ke Wi-Fi. Cek ulang password atau sinyal Wi-Fi.");
  }
#endif

#ifdef TEST_HTTP_REQUEST
  Serial.println(">>> MODE TES 3: HTTP Request ke Internet <<<");
  Serial.println("Tes ini membutuhkan koneksi Wi-Fi.");
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println("\nWiFi terhubung. Mengirim request ke http://example.com...");
  
  HTTPClient http;
  http.begin("http://example.com/");
  int httpCode = http.GET();
  if (httpCode > 0) {
    Serial.printf("--> HASIL: SUKSES! Server merespon dengan kode: %d\n", httpCode);
  } else {
    Serial.printf("--> HASIL: GAGAL! Tidak bisa berkomunikasi dengan internet. Error: %s\n", http.errorToString(httpCode).c_str());
  }
  http.end();
#endif

#ifdef TEST_ANALOG_PIN_34
  Serial.println(">>> MODE TES 4: Analog Pin 33 (ECG) <<<");
  Serial.println("Membaca nilai mentah dari pin 34 setiap 1 detik. Cabut semua kabel dari pin 34.");
  Serial.println("Nilai normal harusnya acak/berfluktuasi. Jika selalu 0 atau 4095, pin mungkin rusak.");
#endif

#ifdef TEST_DIGITAL_PINS
  Serial.println(">>> MODE TES 5: Digital Pins (LO+/LO-/SDN) <<<");
  pinMode(25, INPUT_PULLUP); // Pin LO+
  pinMode(26, INPUT_PULLUP); // Pin LO-
  pinMode(27, OUTPUT);       // Pin SDN
  Serial.println("Pin 27 (SDN) sekarang diset HIGH (ON).");
  digitalWrite(27, HIGH);
  Serial.println("Silakan tes pin 25 & 26 dengan cara menghubungkannya ke GND.");
#endif

  Serial.println("\n--- Tes Selesai ---");
}

void loop() {
#ifdef TEST_ANALOG_PIN_34
  int rawValue = analogRead(32);
  Serial.printf("Nilai Pin 32: %d\n", rawValue);
  delay(1000);
#endif

#ifdef TEST_DIGITAL_PINS
  int loPlus = digitalRead(25);
  int loMinus = digitalRead(26);
  Serial.printf("Status Pin -> LO+(25): %s | LO-(26): %s\n", loPlus == HIGH ? "HIGH (Lepas)" : "LOW (Nempel)", loMinus == HIGH ? "HIGH (Lepas)" : "LOW (Nempel)");
  delay(1000);
#endif
}
