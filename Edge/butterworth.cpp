#include "ButterworthFilter.h"

// Implementasi Konstruktor
ButterworthFilter::ButterworthFilter() {
  // Koefisien untuk filter Butterworth Orde 3, Bandpass 0.5-40 Hz, Fs=360Hz
  a[0] = 1.0;
  a[1] = -2.22498772;
  a[2] = 1.90591593;
  a[3] = -0.63004815;

  b[0] = 0.18475754;
  b[1] = -0.11728189;
  b[2] = -0.11728189;
  b[3] = 0.18475754;

  // Panggil reset untuk memastikan state awal adalah nol
  reset();
}

// Implementasi fungsi reset
void ButterworthFilter::reset() {
  for (int i = 0; i < 3; i++) {
    w[i] = 0.0f; // 'f' menandakan tipe float
  }
}

// Implementasi fungsi update dengan struktur Direct Form II Transposed
float ButterworthFilter::update(float newSample) {
  float output = b[0] * newSample + w[0];
  
  w[0] = b[1] * newSample - a[1] * output + w[1];
  w[1] = b[2] * newSample - a[2] * output + w[2];
  w[2] = b[3] * newSample - a[3] * output;
  
  return output;
}