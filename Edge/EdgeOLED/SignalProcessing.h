#ifndef SIGNAL_PROCESSING_H
#define SIGNAL_PROCESSING_H

#include <Arduino.h>

void processECG(const float* signal, int len, int fs, int &bpm, float &hrv);

#endif
