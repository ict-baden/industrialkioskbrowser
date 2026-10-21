package de.ictbaden.industrialkioskbrowser.scanner

import android.content.Context
import android.os.Build
import android.util.Log
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.GenericIntent

/**
 * Owns the vendor scanner plus the generic intent receiver for one screen. Call [start] in
 * `onResume` and [stop] in `onPause`; scans arriving while disabled are dropped.
 */
class ScannerManager(
    context: Context,
    genericIntent: GenericIntent = GenericIntent.AUTO,
    private val onScan: (ScanResult) -> Unit,
) {
    private val appContext = context.applicationContext
    val scanner: BarcodeScanner = create(appContext)

    /** Exported receiver for `<package>.action.BARCODE`, see [GenericIntent]. */
    private val generic = GenericIntentReceiver(appContext).takeIf { acceptsGenericIntent(genericIntent, scanner) }
    private var running = false

    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (running) scanner.setEnabled(value)
        }

    fun start() {
        if (running) return
        running = true
        val deliver: (ScanResult) -> Unit = { scan ->
            if (enabled) onScan(scan) else Log.i(TAG, "Scan ignored, scanner disabled")
        }
        runCatching { scanner.start(deliver) }.onFailure { Log.e(TAG, "Starting ${scanner.name} failed", it) }
        generic?.start(deliver)
        runCatching { scanner.setEnabled(enabled) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { scanner.stop() }.onFailure { Log.e(TAG, "Stopping ${scanner.name} failed", it) }
        generic?.stop()
    }

    fun softTrigger(start: Boolean?) {
        if (running && enabled) scanner.softTrigger(start)
    }

    companion object {
        private const val TAG = "ScannerManager"

        fun acceptsGenericIntent(mode: GenericIntent, scanner: BarcodeScanner) = when (mode) {
            GenericIntent.ON -> true
            GenericIntent.OFF -> false
            GenericIntent.AUTO -> scanner is NoScanner
        }

        /** Scanner name for the user interface; "no scanner" is translated, product names are not. */
        fun displayName(context: Context): String =
            create(context).let { if (it is NoScanner) context.getString(R.string.no_scanner) else it.name }

        fun create(context: Context): BarcodeScanner {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return when {
                manufacturer.contains("zebra") || manufacturer.contains("motorola solutions") ->
                    DataWedgeScanner(context)
                manufacturer.contains("honeywell") -> HoneywellScanner(context)
                else -> NoScanner()
            }
        }
    }
}
