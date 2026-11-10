package de.ictbaden.industrialkioskbrowser.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import de.ictbaden.industrialkioskbrowser.config.ScanEngine

/**
 * Finds out once per process whether the device has a scan engine (issue #48).
 *
 * Zebra: DataWedge lists its scanners (`ENUMERATE_SCANNERS`); a scan engine is there when the list
 * has a built-in scanner other than the camera or a connected external one ([hasScanEngine]).
 * Honeywell: the scanner service is installed. Everything else has none. Until DataWedge answers, the scan engine counts as present – that keeps the behaviour of
 * earlier versions on Zebra devices whose DataWedge does not answer.
 */
object ScanEngineDetector {

    enum class State { UNKNOWN, PRESENT, ABSENT }

    /** What a screen uses for scanning. */
    enum class Choice { HARDWARE, CAMERA, NONE }

    /** One entry of the DataWedge scanner list. */
    data class ScannerInfo(val name: String?, val identifier: String?, val connected: Boolean?)

    private const val TAG = "ScanEngine"
    private const val TIMEOUT_MS = 2_000L
    private const val API = "com.symbol.datawedge.api"
    private const val RESULT_ACTION = "$API.RESULT_ACTION"
    private const val EXTRA_ENUMERATE = "$API.RESULT_ENUMERATE_SCANNERS"
    const val HONEYWELL_PACKAGE = "com.intermec.datacollectionservice"

    @Volatile
    var state: State = State.UNKNOWN
        private set

    private val listeners = mutableSetOf<() -> Unit>()
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var started = false

    /** Called by the application on start; later calls do nothing. Main thread. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        when (vendor()) {
            Vendor.ZEBRA -> if (installed(app, DataWedgeScanner.DATAWEDGE_PACKAGE)) enumerateDataWedge(app) else update(State.ABSENT, "DataWedge not installed")
            Vendor.HONEYWELL -> update(if (installed(app, HONEYWELL_PACKAGE)) State.PRESENT else State.ABSENT, "Honeywell scanner service")
            Vendor.OTHER -> update(State.ABSENT, "no vendor scanner service (${Build.MANUFACTURER})")
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /**
     * Whether a DataWedge scanner list contains a scan engine. DataWedge lists every scanner it
     * supports, also Bluetooth and USB scanners that were never paired (seen on a TC22/HC20), so only
     * a built-in one (`INTERNAL_IMAGER`, `INTERNAL_LASER` …, not `INTERNAL_CAMERA`) or a connected
     * external scanner counts.
     */
    fun hasScanEngine(scanners: List<ScannerInfo>): Boolean = scanners.any { scanner ->
        !isCamera(scanner) && (scanner.identifier.orEmpty().startsWith("INTERNAL_", ignoreCase = true) || scanner.connected == true)
    }

    fun isCamera(scanner: ScannerInfo): Boolean =
        listOfNotNull(scanner.identifier, scanner.name).any { it.contains("camera", ignoreCase = true) }

    /**
     * Which scanner a screen uses. [vendor]: the device has a vendor scanner service we drive
     * (Zebra, Honeywell). In [ScanEngine.AUTO] the camera is only taken when the detection found no
     * scan engine; while it is still running the scan engine wins.
     */
    fun choose(engine: ScanEngine, vendor: Boolean, state: State, hasCamera: Boolean): Choice = when (engine) {
        ScanEngine.HARDWARE -> if (vendor) Choice.HARDWARE else Choice.NONE
        ScanEngine.CAMERA -> if (hasCamera) Choice.CAMERA else Choice.NONE
        ScanEngine.AUTO -> when {
            vendor && state != State.ABSENT -> Choice.HARDWARE
            hasCamera -> Choice.CAMERA
            vendor -> Choice.HARDWARE
            else -> Choice.NONE
        }
    }

    fun hasCamera(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    internal enum class Vendor { ZEBRA, HONEYWELL, OTHER }

    internal fun vendor(manufacturer: String = Build.MANUFACTURER): Vendor {
        val m = manufacturer.lowercase()
        return when {
            m.contains("zebra") || m.contains("motorola solutions") -> Vendor.ZEBRA
            m.contains("honeywell") -> Vendor.HONEYWELL
            else -> Vendor.OTHER
        }
    }

    private fun enumerateDataWedge(context: Context) {
        var receiver: BroadcastReceiver? = null
        val timeout = Runnable {
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            receiver = null
            Log.w(TAG, "DataWedge did not list its scanners within $TIMEOUT_MS ms, assuming a scan engine")
        }
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!intent.hasExtra(EXTRA_ENUMERATE)) return
                val list = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_ENUMERATE, Bundle::class.java).orEmpty()
                val scanners = list.map {
                    ScannerInfo(
                        name = it.getString("SCANNER_NAME"),
                        identifier = it.getString("SCANNER_IDENTIFIER"),
                        connected = if (it.containsKey("SCANNER_CONNECTION_STATE")) it.getBoolean("SCANNER_CONNECTION_STATE") else null,
                    )
                }
                main.removeCallbacks(timeout)
                runCatching { context.unregisterReceiver(this) }
                receiver = null
                Log.i(TAG, "DataWedge scanners: ${scanners.joinToString { "${it.name} [${it.identifier}, connected=${it.connected}]" }}")
                update(if (hasScanEngine(scanners)) State.PRESENT else State.ABSENT, "DataWedge list")
            }
        }
        val filter = IntentFilter(RESULT_ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        // DataWedge is a separate app, so the receiver has to be exported.
        ContextCompat.registerReceiver(context, resultReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = resultReceiver
        main.postDelayed(timeout, TIMEOUT_MS)
        val request = Intent("$API.ACTION")
            .setPackage(DataWedgeScanner.DATAWEDGE_PACKAGE)
            .putExtra("$API.ENUMERATE_SCANNERS", "")
            .putExtra("SEND_RESULT", "true")
            .putExtra("COMMAND_IDENTIFIER", "industrialkioskbrowser-enumerate")
        runCatching { context.sendBroadcast(request) }.onFailure { Log.w(TAG, "Asking DataWedge failed", it) }
    }

    private fun update(newState: State, reason: String) {
        Log.i(TAG, "Scan engine: $newState ($reason)")
        if (state == newState) return
        state = newState
        main.post { listeners.toList().forEach { it() } }
    }

    private fun installed(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
