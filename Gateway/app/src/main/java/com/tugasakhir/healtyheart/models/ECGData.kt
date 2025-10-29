package com.tugasakhir.healtyheart.models

data class ECGData(
    val id: String,
    val timestamp: String,
    val deviceName: String,
    // Data ECG setelah filter Butterworth
    val value: Float,
    val label: String // "Normal", "Atrial Fibrillation", "Pre Ventricular Contraction"
)
