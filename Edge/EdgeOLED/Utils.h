#ifndef UTILS_H
#define UTILS_H

#include <Arduino.h>
#include <BLEServer.h>

// Deklarasi fungsi-fungsi
String getDeviceIdentity();
void setupOperationalBLE(BLEServer* &pServer, BLECharacteristic* &pCharacteristic, BLEServerCallbacks* callbacks);
bool isSignalValid(int loPlusPin, int loMinusPin);
String getTimestamp();
void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* beatBuffer, float* afibBuffer, int length);
void sensorSleep();
void sensorWakeUp();
void handleFactoryReset();

#endif
