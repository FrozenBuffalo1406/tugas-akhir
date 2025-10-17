#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "Display.h"
#include "config.h"

// Buat objek display
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET_PIN);

// Buffer untuk menyimpan data waveform yang akan ditampilkan
int waveformBuffer[SCREEN_WIDTH];
int waveformIndex = 0;
unsigned long lastDisplayTime = 0;

void setupDisplay() {
    if(!display.begin(SSD1306_SWITCHCAPVCC, OLED_I2C_ADDR)) { 
        Serial.println(F("Alokasi SSD1306 gagal"));
        for(;;); // Loop selamanya jika display tidak terdeteksi
    }
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0,0);
    display.println("Starting ECG Device...");
    display.display();
    delay(1000);

    // Inisialisasi buffer waveform
    for(int i=0; i<SCREEN_WIDTH; i++) {
        waveformBuffer[i] = SCREEN_HEIGHT / 2; // Mulai dari tengah layar
    }
}

void displayMessage(const String& msg) {
    display.clearDisplay();
    display.setCursor(0,0);
    display.println(msg);
    display.display();
}

void updateDisplay(int bpm, float hrv, const String& label, float newEcgPoint) {
    // Batasi update layar agar tidak terlalu cepat (misal: 25fps)
    if (millis() - lastDisplayTime < 40) {
        return;
    }
    lastDisplayTime = millis();

    // 1. Tambahkan titik data baru ke buffer waveform
    // Normalisasi nilai ECG (0-4095) ke tinggi layar (misal: 32-63)
    int scaledValue = map(newEcgPoint, 0, 4095, SCREEN_HEIGHT, SCREEN_HEIGHT / 2);
    waveformBuffer[waveformIndex] = constrain(scaledValue, SCREEN_HEIGHT / 2, SCREEN_HEIGHT - 1);
    
    waveformIndex++;
    if (waveformIndex >= SCREEN_WIDTH) {
        waveformIndex = 0;
    }

    // 2. Bersihkan layar
    display.clearDisplay();

    // 3. Gambar Header (BPM, HRV, Status)
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.print("BPM:");
    display.print(bpm);

    display.setCursor(64, 0);
    display.print("HRV:");
    display.print(hrv, 1);
    
    display.setCursor(0, 10);
    display.print("Status: ");
    display.print(label);

    // 4. Gambar Waveform
    for (int x = 0; x < SCREEN_WIDTH; x++) {
        int bufferPos = (waveformIndex + x) % SCREEN_WIDTH;
        display.drawPixel(x, waveformBuffer[bufferPos], SSD1306_WHITE);
    }
    
    // 5. Tampilkan ke layar
    display.display();
}
