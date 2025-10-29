#include "SignalProcessing.h"

// Fungsi ini adalah implementasi yang sangat sederhana untuk demo.
// Untuk hasil akurat, algoritma yang lebih canggih (misal: Pan-Tompkins) sangat disarankan.
void processECG(const float* signal, int len, int fs, int &bpm, float &hrv) {
    float peakThreshold = 2500; // Threshold awal, perlu disesuaikan
    int lastPeakIndex = -1;
    const int minPeakDistance = fs * 0.3; // Jarak minimum antar detak (30% dari 1 detik)
    
    int rrIntervals[20]; // Simpan hingga 20 interval detak
    int intervalCount = 0;

    // 1. Cari Puncak (R-peaks)
    for (int i = 1; i < len - 1; i++) {
        if (signal[i] > peakThreshold && signal[i] > signal[i-1] && signal[i] > signal[i+1]) {
            if (lastPeakIndex == -1 || (i - lastPeakIndex) > minPeakDistance) {
                if (lastPeakIndex != -1 && intervalCount < 20) {
                    rrIntervals[intervalCount] = i - lastPeakIndex;
                    intervalCount++;
                }
                lastPeakIndex = i;
                i += minPeakDistance; // Langsung loncat untuk menghindari deteksi ganda
            }
        }
    }

    if (intervalCount < 2) {
        bpm = 0;
        hrv = 0.0;
        return; // Butuh minimal 2 interval untuk kalkulasi
    }

    // 2. Hitung BPM
    long totalInterval = 0;
    for(int i = 0; i < intervalCount; i++) {
        totalInterval += rrIntervals[i];
    }
    float avgIntervalSamples = (float)totalInterval / intervalCount;
    bpm = (60.0 * fs) / avgIntervalSamples;

    // 3. Hitung HRV (SDRR - Standard Deviation of RR intervals)
    float avgIntervalMs = (avgIntervalSamples * 1000.0) / fs;
    float sumOfSquares = 0.0;
    for(int i = 0; i < intervalCount; i++) {
        float intervalMs = (rrIntervals[i] * 1000.0) / fs;
        sumOfSquares += pow(intervalMs - avgIntervalMs, 2);
    }
    hrv = sqrt(sumOfSquares / (intervalCount - 1));
}
