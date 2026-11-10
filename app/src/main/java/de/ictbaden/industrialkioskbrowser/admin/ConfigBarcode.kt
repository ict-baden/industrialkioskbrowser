package de.ictbaden.industrialkioskbrowser.admin

import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.DataTooLongException
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment

/** QR code of a configuration for "Export as barcode" (payload from [de.ictbaden.industrialkioskbrowser.config.ConfigCodec]). */
object ConfigBarcode {

    /** Light area around the code in modules, as the QR standard asks for. */
    const val QUIET_ZONE = 4

    /**
     * The QR code for [payload], or `null` when it does not fit even a version 40 code. Error
     * correction M where it fits (raised further when that costs no size), otherwise L.
     */
    fun encode(payload: String): QrCode? {
        val segments = QrSegment.makeSegments(payload)
        for (ecc in listOf(QrCode.Ecc.MEDIUM, QrCode.Ecc.LOW)) {
            try {
                return QrCode.encodeSegments(segments, ecc, QrCode.MIN_VERSION, QrCode.MAX_VERSION, -1, true)
            } catch (_: DataTooLongException) {
                // try the next, lower level
            }
        }
        return null
    }

    /** Black modules on white with the quiet zone, [pixelsPerModule] pixels per module. */
    fun bitmap(qr: QrCode, pixelsPerModule: Int): Bitmap {
        val modules = qr.size + 2 * QUIET_ZONE
        val size = modules * pixelsPerModule
        val pixels = IntArray(size * size) { Color.WHITE }
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (!qr.getModule(x, y)) continue
                val left = (x + QUIET_ZONE) * pixelsPerModule
                val top = (y + QUIET_ZONE) * pixelsPerModule
                for (dy in 0 until pixelsPerModule) {
                    val row = (top + dy) * size
                    for (dx in 0 until pixelsPerModule) pixels[row + left + dx] = Color.BLACK
                }
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
