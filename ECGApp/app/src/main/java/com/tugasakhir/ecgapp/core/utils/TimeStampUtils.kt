// com/proyeklo/ecgapp/core/utils/TimestampUtils.kt
package com.tugasakhir.ecgapp.core.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Mengubah string ISO 8601 (cth: "2025-11-14T07:12:02Z")
 * menjadi format yang lebih gampang dibaca (cth: "14 Nov 2025, 07:12").
 */
fun formatTimestamp(isoTimestamp: String): String {
    return try {
        // Pola formatter buat ngebaca "dd MMM yyyy, HH:mm"
        val outputFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")

        // Parse ISO timestamp (OffsetDateTime lebih aman buat handle 'Z' / timezone)
        val dateTime = OffsetDateTime.parse(isoTimestamp)

        // Format ke string
        dateTime.format(outputFormatter)
    } catch (e: DateTimeParseException) {
        // Kalo formatnya aneh, balikin string aslinya aja
        isoTimestamp
    }
}