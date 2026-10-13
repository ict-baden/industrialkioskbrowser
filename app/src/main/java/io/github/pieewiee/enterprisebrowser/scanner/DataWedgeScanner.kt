package io.github.pieewiee.enterprisebrowser.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Zebra DataWedge integration via its intent API.
 *
 * On start a DataWedge profile bound to this app is created (if missing) with intent output to
 * [resultAction] and keystroke output disabled. Decoder settings are left to the administrator,
 * so tuning in the DataWedge app or via StageNow survives restarts.
 */
class DataWedgeScanner(private val context: Context) : BarcodeScanner {
    override val name = "Zebra DataWedge"

    private val resultAction = "${context.packageName}.DATAWEDGE_SCAN"
    private var receiver: BroadcastReceiver? = null

    override fun start(onScan: (ScanResult) -> Unit) {
        if (receiver != null) return
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != resultAction) return
                val data = intent.getStringExtra(EXTRA_DATA) ?: return
                val symbology = intent.getStringExtra(EXTRA_LABEL_TYPE)?.removePrefix("LABEL-TYPE-")
                onScan(ScanResult(data, symbology, name))
            }
        }
        val filter = IntentFilter(resultAction).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        // DataWedge is a separate app, so the receiver has to be exported.
        ContextCompat.registerReceiver(context, scanReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = scanReceiver
        configureProfile()
    }

    override fun stop() {
        receiver?.let(context::unregisterReceiver)
        receiver = null
    }

    override fun setEnabled(enabled: Boolean) {
        send("$API.SCANNER_INPUT_PLUGIN", if (enabled) "ENABLE_PLUGIN" else "DISABLE_PLUGIN")
    }

    override fun softTrigger(start: Boolean?) {
        val command = when (start) {
            true -> "START_SCANNING"
            false -> "STOP_SCANNING"
            null -> "TOGGLE_SCANNING"
        }
        send("$API.SOFT_SCAN_TRIGGER", command)
    }

    private fun configureProfile() {
        val barcode = plugin("BARCODE", reset = false) {
            putString("scanner_selection", "auto")
            putString("scanner_input_enabled", "true")
        }
        val intentOutput = plugin("INTENT", reset = true) {
            putString("intent_output_enabled", "true")
            putString("intent_action", resultAction)
            putString("intent_category", Intent.CATEGORY_DEFAULT)
            putString("intent_delivery", "2") // broadcast
        }
        val keystroke = plugin("KEYSTROKE", reset = true) {
            putString("keystroke_output_enabled", "false")
        }
        val app = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val profile = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putParcelableArray("APP_LIST", arrayOf(app))
            putParcelableArrayList("PLUGIN_CONFIG", arrayListOf(barcode, intentOutput, keystroke))
        }
        send("$API.SET_CONFIG", profile)
    }

    private fun plugin(name: String, reset: Boolean, params: Bundle.() -> Unit) = Bundle().apply {
        putString("PLUGIN_NAME", name)
        putString("RESET_CONFIG", reset.toString())
        putBundle("PARAM_LIST", Bundle().apply(params))
    }

    private fun send(extra: String, value: String) =
        context.sendBroadcast(apiIntent().putExtra(extra, value))

    private fun send(extra: String, value: Bundle) =
        context.sendBroadcast(apiIntent().putExtra(extra, value))

    private fun apiIntent() = Intent("$API.ACTION").setPackage(DATAWEDGE_PACKAGE)

    companion object {
        const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"
        private const val API = "com.symbol.datawedge.api"
        private const val PROFILE_NAME = "EnterpriseBrowser"
        private const val EXTRA_DATA = "com.symbol.datawedge.data_string"
        private const val EXTRA_LABEL_TYPE = "com.symbol.datawedge.label_type"
    }
}
