package de.ictbaden.industrialkioskbrowser.scanner

/**
 * Scans with the camera on devices without a scan engine (issue #48). The trigger opens
 * [CameraScanActivity] through [launch]; the hosting activity hands the result to
 * [ScannerManager.deliver].
 */
class CameraScanner(private val launch: () -> Unit) : BarcodeScanner {
    override val name = NAME
    private var enabled = true

    override fun start(onScan: (ScanResult) -> Unit) = Unit

    override fun stop() = Unit

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /** Start or toggle opens the camera; stop does nothing (the camera screen closes itself). */
    override fun softTrigger(start: Boolean?) {
        if (enabled && start != false) launch()
    }

    companion object {
        /** Name for the JS bridge and the scan source; the user interface shows a translated text. */
        const val NAME = "Camera"
    }
}
