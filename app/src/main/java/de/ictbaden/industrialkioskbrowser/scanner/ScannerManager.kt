package de.ictbaden.industrialkioskbrowser.scanner

import android.content.Context
import android.util.Log
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.GenericIntent
import de.ictbaden.industrialkioskbrowser.config.ScanEngine
import de.ictbaden.industrialkioskbrowser.scanner.ScanEngineDetector.Choice

/**
 * Owns the scanner plus the generic intent receiver for one screen. Call [start] in
 * `onResume` and [stop] in `onPause`; scans arriving while disabled are dropped.
 *
 * [launchCamera] opens the camera scanner ([CameraScanActivity]); the hosting activity hands its
 * result to [deliver]. Without it the camera is never chosen. [onScannerChanged] is called when the
 * scan engine detection finishes after this screen started and the scanner changes.
 */
class ScannerManager(
    context: Context,
    private val genericIntent: GenericIntent = GenericIntent.AUTO,
    private val engine: ScanEngine = ScanEngine.AUTO,
    private val launchCamera: (() -> Unit)? = null,
    private val onScannerChanged: (() -> Unit)? = null,
    private val onScan: (ScanResult) -> Unit,
) {
    private val appContext = context.applicationContext
    var scanner: BarcodeScanner = create(appContext, engine, launchCamera)
        private set
    private var createdWith = ScanEngineDetector.state

    /** Exported receiver for `<package>.action.BARCODE`, see [GenericIntent]. */
    private var generic: GenericIntentReceiver? = null
    private var running = false
    private val detectionListener: () -> Unit = {
        if (running && reevaluate()) {
            restart()
            onScannerChanged?.invoke()
        }
    }

    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (running) scanner.setEnabled(value)
        }

    private val deliverIfEnabled: (ScanResult) -> Unit = { scan ->
        if (enabled) onScan(scan) else Log.i(TAG, "Scan ignored, scanner disabled")
    }

    fun start() {
        if (running) return
        if (reevaluate()) onScannerChanged?.invoke()
        running = true
        ScanEngineDetector.addListener(detectionListener)
        startScanner()
    }

    fun stop() {
        if (!running) return
        running = false
        ScanEngineDetector.removeListener(detectionListener)
        stopScanner()
    }

    fun softTrigger(start: Boolean?) {
        if (running && enabled) scanner.softTrigger(start)
    }

    /** A scan from the camera screen. It returns while this screen is paused, so `running` is not checked. */
    fun deliver(scan: ScanResult) = deliverIfEnabled(scan)

    /** Picks the scanner again when the detection changed since it was created; `true` if it changed. */
    private fun reevaluate(): Boolean {
        val state = ScanEngineDetector.state
        if (state == createdWith) return false
        createdWith = state
        val next = create(appContext, engine, launchCamera)
        if (next::class == scanner::class) return false
        Log.i(TAG, "Scanner changes from ${scanner.name} to ${next.name}")
        if (running) stopScanner()
        scanner = next
        return true
    }

    private fun restart() {
        stopScanner()
        startScanner()
    }

    private fun startScanner() {
        runCatching { scanner.start(deliverIfEnabled) }.onFailure { Log.e(TAG, "Starting ${scanner.name} failed", it) }
        generic = if (acceptsGenericIntent(genericIntent, scanner)) GenericIntentReceiver(appContext).also { it.start(deliverIfEnabled) } else null
        runCatching { scanner.setEnabled(enabled) }
    }

    private fun stopScanner() {
        runCatching { scanner.stop() }.onFailure { Log.e(TAG, "Stopping ${scanner.name} failed", it) }
        generic?.stop()
        generic = null
    }

    companion object {
        private const val TAG = "ScannerManager"

        /** AUTO accepts the generic broadcast only without a vendor scanner (the camera is not one). */
        fun acceptsGenericIntent(mode: GenericIntent, scanner: BarcodeScanner) = when (mode) {
            GenericIntent.ON -> true
            GenericIntent.OFF -> false
            GenericIntent.AUTO -> scanner is NoScanner || scanner is CameraScanner
        }

        /** Scanner name for the user interface; "no scanner" and "camera" are translated, product names are not. */
        fun displayName(context: Context, engine: ScanEngine = ScanEngine.AUTO): String =
            when (val scanner = create(context, engine, launchCamera = {})) {
                is NoScanner -> context.getString(R.string.no_scanner)
                is CameraScanner -> context.getString(R.string.camera_scanner)
                else -> scanner.name
            }

        fun create(context: Context, engine: ScanEngine = ScanEngine.AUTO, launchCamera: (() -> Unit)? = null): BarcodeScanner {
            val vendor = ScanEngineDetector.vendor()
            val choice = ScanEngineDetector.choose(
                engine = engine,
                vendor = vendor != ScanEngineDetector.Vendor.OTHER,
                state = ScanEngineDetector.state,
                hasCamera = launchCamera != null && ScanEngineDetector.hasCamera(context),
            )
            return when (choice) {
                Choice.HARDWARE -> when (vendor) {
                    ScanEngineDetector.Vendor.ZEBRA -> DataWedgeScanner(context)
                    ScanEngineDetector.Vendor.HONEYWELL -> HoneywellScanner(context)
                    ScanEngineDetector.Vendor.OTHER -> NoScanner()
                }
                Choice.CAMERA -> CameraScanner(launchCamera ?: {})
                Choice.NONE -> NoScanner()
            }
        }
    }
}
