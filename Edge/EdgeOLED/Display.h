#ifndef DISPLAY_H
#define DISPLAY_H

#include <Arduino.h>

void setupDisplay();
void updateDisplay(int bpm, float hrv, const String& label, float newEcgPoint);
void displayMessage(const String& msg);

#endif
