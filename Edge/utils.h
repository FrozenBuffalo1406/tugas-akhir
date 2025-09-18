#ifndef UTILS_H
#define UTILS_H

#include <Arduino.h>
#include <BLEDevice.h>
#include "ButterworthFilter.h"

// Deklarasi fungsi-fungsi helper.
// Perhatikan bagaimana kita menambahkan parameter untuk variabel yang tadinya global.

void setupBLE(BLEServer* &pServer, BLECharacteristic* &pCharacteristic);

float readAndFilterECG(ButterworthFilter &filter, int pin);

void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* buffer, int length);

String getTimestamp();

#endif