#ifndef UTILS_H
#define UTILS_H

#include <Arduino.h>
#include <BLEServer.h>

String getTimestamp();
bool isSignalValid(int loPlusPin, int loMinusPin);
String getDeviceIdentity(); 
void sendDataToServer(const char* url, const char* deviceId, const char* timestamp, float* beatBuffer, int length);
void sensorSleep();
void sensorWakeUp();
void handleFactoryReset();

#endif
