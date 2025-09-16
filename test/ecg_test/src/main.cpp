#include <Arduino.h>
#include "butterworth.h" // Include custom library filter Butterworth kamu

// --- Konfigurasi Pin ---
const int ECG_ANALOG_PIN = 34; // Pin GPIO tempat output AD8232 terhubung (harus pin ADC)
const int BUZZER_PIN = 2;      // Pin GPIO untuk buzzer
const int LO_MINUS_PIN = 35;   // LO- pin ke GPIO35
const int LO_PLUS_PIN = 32;    // LO+ pin ke GPIO32
const int SDN_PIN = 4;         // SDN pin ke GPIO4

// --- Konfigurasi Pembacaan Sensor ---
const int SAMPLE_RATE = 250; // Sample rate data ECG (samples per second)
const int READ_INTERVAL_US = 1000000 / SAMPLE_RATE; // Interval pembacaan sensor dalam mikrodetik

// --- Konfigurasi Filter Butterworth ---
// PENTING: Ganti dengan koefisien B dan A yang kamu dapat dari script Python!
const double b_coeffs[] = {0.0219612634, 0.0000000000, -0.0878450537, 
  0.0000000000, 0.1317675806, 0.0000000000, -0.0878450537, 0.0000000000, 0.0219612634};
const double a_coeffs[] = {1.0000000000, -5.4061545144, 12.7689074569, -17.4196484106, 15.1907165279, -8.7164421149, 3.1973474110, -0.6803537539, 0.0656274072};
const int FILTER_ORDER = 8; 

// Inisialisasi objek filter Butterworth
FilterButterworth bp_filter(b_coeffs, a_coeffs, FILTER_ORDER);

// Variabel Global
float raw_ecg_value;
float filtered_ecg_value;
unsigned long previousMicros = 0; // Untuk timing pembacaan sensor
bool isECGActive = true; // Status ECG, default aktif

// --- Fungsi Pra-pemrosesan Sinyal ECG ---
float preprocessECG(float raw_signal) {
  return bp_filter.filter(raw_signal);
}

// --- Fungsi Cek Lead-Off yang diperbarui ---
bool checkLeadOff() {
  // Gunakan pull-up resistor untuk menstabilkan sinyal
  if (digitalRead(LO_MINUS_PIN) == HIGH && digitalRead(LO_PLUS_PIN) == HIGH) {
    return false; // Kedua elektroda terpasang = lead ON
  }
  return true; // Salah satu atau keduanya LOW = lead OFF
}

// --- Fungsi Kontrol SDN (On/Off ECG) ---
void setECGPower(bool state) {
  if (state) {
    digitalWrite(SDN_PIN, HIGH); // Aktifkan AD8232
    isECGActive = true;
    Serial.println("AD8232 is ON.");
  } else {
    digitalWrite(SDN_PIN, LOW); // Matikan AD8232
    isECGActive = false;
    Serial.println("AD8232 is OFF.");
  }
}


// --- Setup ---
void setup() {
  Serial.begin(115200); // Inisialisasi Serial Monitor
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW); // Buzzer mati di awal
  
  // Mengaktifkan pull-up resistor internal
  pinMode(LO_MINUS_PIN, INPUT_PULLUP);
  pinMode(LO_PLUS_PIN, INPUT_PULLUP);
  
  pinMode(SDN_PIN, OUTPUT);
  setECGPower(true);
}

// --- Loop Utama ---
void loop() {
  unsigned long currentMicros = micros(); 
  if (currentMicros - previousMicros >= READ_INTERVAL_US) {
    previousMicros = currentMicros;
    if (isECGActive) {
      bool leadOff = checkLeadOff();
      if (leadOff) {
        Serial.println("WARNING: ECG Leads OFF!");
        digitalWrite(BUZZER_PIN, HIGH);
      } else {
        digitalWrite(BUZZER_PIN, LOW);
        raw_ecg_value = analogRead(ECG_ANALOG_PIN);
        filtered_ecg_value = preprocessECG(raw_ecg_value);

        Serial.print("Raw: ");
        Serial.print(raw_ecg_value);
        Serial.print(", Filtered: ");
        Serial.print(filtered_ecg_value);
        Serial.print(", Leads: ON");
        Serial.println();
      }
    } else {
      Serial.println("AD8232 is currently OFF (via SDN). No data.");
    }
  }

  // Untuk test on/off ECG via Serial Monitor
  if (Serial.available()) {
    char command = Serial.read();
    if (command == '1') {
      setECGPower(true);
    } else if (command == '0') {
      setECGPower(false);
    }
  }
}
