#ifndef UTILS_H
#define UTILS_H

#include <Arduino.h>
#include <BLEServer.h>
#include "ButterworthFilter.h"

// Deklarasi fungsi-fungsi
void setupOperationalBLE(BLEServer* &pServer, BLECharacteristic* &pCharacteristic, BLEServerCallbacks* callbacks);
void startBleProvisioning(BLEServer* &pServer, BLECharacteristicCallbacks* callbacks);
bool provisionViaWifi(const char* registerUrl);
bool isSignalValid(int loPlusPin, int loMinusPin);
String getTimestamp();
void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* beatBuffer, float* afibBuffer, int length);

#endif