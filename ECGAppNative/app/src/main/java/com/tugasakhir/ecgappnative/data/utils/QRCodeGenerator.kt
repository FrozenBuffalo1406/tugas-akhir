package com.tugasakhir.ecgappnative.data.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import kotlin.text.get

object QRCodeGenerator {

    /**
     * Meng-generate QR Code dari teks (String) menjadi Bitmap.
     * @param text Teks yang akan di-encode (dalam kasus ini, User ID).
     * @param size Ukuran bitmap (persegi, misal 400x400).
     * @return Bitmap QR Code, atau null jika gagal.
     */
    fun generate(text: String, size: Int): Bitmap? {
        val bitMatrix = try {
            MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                size,
                size,
                null
            )
        } catch (e: WriterException) {
            e.printStackTrace()
            return null
        }

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}