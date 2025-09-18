#ifndef BUTTERWORTH_FILTER_H
#define BUTTERWORTH_FILTER_H

class ButterworthFilter {
public:
  // Konstruktor
  ButterworthFilter();

  // Fungsi untuk memfilter satu sampel data. Nama 'update' lebih umum
  // untuk filter real-time daripada 'filter'.
  float update(float newSample);
  
  // Fungsi untuk mereset state filter jika diperlukan
  void reset();

private:
  // Koefisien filter langsung didefinisikan di sini (statis)
  float a[4];
  float b[4];
  
  // State internal filter, hanya butuh satu array 'w'
  float w[3];
};

#endif