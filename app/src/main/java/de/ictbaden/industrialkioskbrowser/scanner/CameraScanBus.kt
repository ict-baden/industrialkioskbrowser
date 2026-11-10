package de.ictbaden.industrialkioskbrowser.scanner

/**
 * Hands codes from the camera screen in continuous mode to the screen that opened it: an activity
 * result arrives only once, when the camera closes. The opening screen sets [listener] while it
 * exists; all calls happen on the main thread.
 */
object CameraScanBus {
    var listener: ((ScanResult) -> Unit)? = null

    fun deliver(scan: ScanResult) {
        listener?.invoke(scan)
    }

    /** Removes [owner]'s listener only if no newer screen replaced it. */
    fun clear(owner: (ScanResult) -> Unit) {
        if (listener === owner) listener = null
    }
}
