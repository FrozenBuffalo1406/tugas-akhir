#include "Callbacks.h"
#include <Arduino.h>

// Memberitahu file ini bahwa variabel & fungsi berikut ada di file lain (.ino)
extern bool deviceConnected;
extern void sensorWakeUp();

// Implementasi detail untuk MyServerCallbacks
void MyServerCallbacks::onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("[BLE] Perangkat terhubung, sensor diaktifkan...");
    sensorWakeUp();
}

void MyServerCallbacks::onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("[BLE] Perangkat terputus");
    pServer->getAdvertising()->start(); // Mulai advertising lagi saat disconnect
}
