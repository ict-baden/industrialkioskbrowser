package de.ictbaden.enterprisebrowser.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Honeywell scanner via the data collection service's intent API: claiming the scanner with
 * data-intent output avoids bundling the proprietary AIDC SDK.
 */
class HoneywellScanner(private val context: Context) : BarcodeScanner {
    override val name = "Honeywell"

    private val resultAction = "${context.packageName}.HONEYWELL_SCAN"
    private var receiver: BroadcastReceiver? = null
    private var triggerActive = false

    override fun start(onScan: (ScanResult) -> Unit) {
        if (receiver != null) return
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != resultAction) return
                val data = intent.getStringExtra("data") ?: return
                triggerActive = false
                onScan(ScanResult(data, intent.getStringExtra("aimId") ?: intent.getStringExtra("codeId"), name))
            }
        }
        ContextCompat.registerReceiver(context, scanReceiver, IntentFilter(resultAction), ContextCompat.RECEIVER_EXPORTED)
        receiver = scanReceiver
        claim()
    }

    override fun stop() {
        release()
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled) claim() else release()
    }

    override fun softTrigger(start: Boolean?) {
        triggerActive = start ?: !triggerActive
        send(Intent(ACTION_CONTROL).putExtra(EXTRA_SCAN, triggerActive))
    }

    private fun claim() {
        val properties = Bundle().apply {
            putBoolean("DPR_DATA_INTENT", true)
            putString("DPR_DATA_INTENT_ACTION", resultAction)
            putString("TRIG_CONTROL_MODE", "autoControl")
        }
        send(
            Intent(ACTION_CLAIM)
                .putExtra(EXTRA_SCANNER, "dcs.scanner.imager")
                .putExtra(EXTRA_PROFILE, "DEFAULT")
                .putExtra(EXTRA_PROPERTIES, properties),
        )
    }

    private fun release() = send(Intent(ACTION_RELEASE))

    private fun send(intent: Intent) = context.sendBroadcast(intent.setPackage(SERVICE_PACKAGE))

    companion object {
        const val SERVICE_PACKAGE = "com.intermec.datacollectionservice"
        private const val ACTION_CLAIM = "com.honeywell.aidc.action.ACTION_CLAIM_SCANNER"
        private const val ACTION_RELEASE = "com.honeywell.aidc.action.ACTION_RELEASE_SCANNER"
        private const val ACTION_CONTROL = "com.honeywell.aidc.action.ACTION_CONTROL_SCANNER"
        private const val EXTRA_SCANNER = "com.honeywell.aidc.extra.EXTRA_SCANNER"
        private const val EXTRA_PROFILE = "com.honeywell.aidc.extra.EXTRA_PROFILE"
        private const val EXTRA_PROPERTIES = "com.honeywell.aidc.extra.EXTRA_PROPERTIES"
        private const val EXTRA_SCAN = "com.honeywell.aidc.extra.EXTRA_SCAN"
    }
}
