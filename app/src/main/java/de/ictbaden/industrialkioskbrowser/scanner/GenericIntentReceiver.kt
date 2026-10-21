package de.ictbaden.industrialkioskbrowser.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Accepts scans from any scanner app configured to broadcast `<package>.action.BARCODE`.
 * Data and symbology are taken from the first extra whose key matches a known naming scheme.
 */
class GenericIntentReceiver(private val context: Context) {
    val action = "${context.packageName}.action.BARCODE"
    private var receiver: BroadcastReceiver? = null

    fun start(onScan: (ScanResult) -> Unit) {
        if (receiver != null) return
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != action) return
                extract(intent.extras)?.let(onScan)
            }
        }
        ContextCompat.registerReceiver(context, scanReceiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
        receiver = scanReceiver
    }

    fun stop() {
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    companion object {
        private val DATA_KEY = Regex("(^|\\.)(data_string|decode_string|barcode_string|barcodeData|data)$", RegexOption.IGNORE_CASE)
        private val SYMBOLOGY_KEY = Regex("(^|\\.)(label_type|symbology_type|symbology|codeId|aimId)$", RegexOption.IGNORE_CASE)

        fun extract(extras: Bundle?): ScanResult? {
            extras ?: return null
            val values = extras.keySet().associateWith { key -> extras.getString(key) }
            val data = values.entries.firstOrNull { (key, value) -> value != null && DATA_KEY.containsMatchIn(key) }?.value
                ?: return null
            val symbology = values.entries.firstOrNull { (key, value) -> value != null && SYMBOLOGY_KEY.containsMatchIn(key) }
                ?.value?.removePrefix("LABEL-TYPE-")
            return ScanResult(data, symbology, "Intent")
        }
    }
}
