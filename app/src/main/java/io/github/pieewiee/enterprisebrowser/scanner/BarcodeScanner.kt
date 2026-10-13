package io.github.pieewiee.enterprisebrowser.scanner

data class ScanResult(
    val data: String,
    val symbology: String?,
    val source: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/** A vendor scanner integration. All calls happen on the main thread. */
interface BarcodeScanner {
    val name: String

    /** Registers for scan results and prepares the vendor service. */
    fun start(onScan: (ScanResult) -> Unit)

    /** Releases everything acquired in [start]. */
    fun stop()

    fun setEnabled(enabled: Boolean)

    /** Starts (`true`), stops (`false`) or toggles (`null`) the scan beam. */
    fun softTrigger(start: Boolean?)
}

/** Used on devices without a supported scanner service; intents are still handled by [GenericIntentReceiver]. */
class NoScanner : BarcodeScanner {
    override val name = "No hardware scanner"
    override fun start(onScan: (ScanResult) -> Unit) = Unit
    override fun stop() = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun softTrigger(start: Boolean?) = Unit
}
