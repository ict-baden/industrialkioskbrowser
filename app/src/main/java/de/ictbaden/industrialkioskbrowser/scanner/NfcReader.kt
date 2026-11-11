package de.ictbaden.industrialkioskbrowser.scanner

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import de.ictbaden.industrialkioskbrowser.config.NfcSettings

/**
 * Reads NFC tags while the browser is in front (`scanner.nfc`, issue #50), with reader mode: no
 * other app gets the tag meanwhile, and Android plays no sound of its own. The text goes to
 * [onScan] like a barcode, with symbology and source `nfc`; [onEmpty] is called for a tag without
 * the data [NfcSettings.read] asks for.
 */
class NfcReader(
    private val activity: Activity,
    private val onScan: (ScanResult) -> Unit,
    private val onEmpty: () -> Unit,
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var settings = NfcSettings()
    private var running = false
    private var tone: ToneGenerator? = null

    /** The device has NFC hardware. */
    val available: Boolean get() = adapter != null

    /** NFC is switched on in the system settings. */
    val switchedOn: Boolean get() = adapter?.isEnabled == true

    fun start(settings: NfcSettings) {
        this.settings = settings
        val nfc = adapter ?: return
        if (running || !settings.enabled) return
        if (settings.beep && tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME) }.getOrNull()
        val options = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_MS) }
        runCatching { nfc.enableReaderMode(activity, ::onTag, FLAGS, options) }
            .onSuccess {
                running = true
                Log.i(TAG, "NFC reader mode on (NFC ${if (nfc.isEnabled) "on" else "off"})")
            }
            .onFailure { Log.w(TAG, "NFC reader mode failed", it) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { adapter?.disableReaderMode(activity) }
        Log.i(TAG, "NFC reader mode off")
    }

    fun release() {
        stop()
        tone?.release()
        tone = null
    }

    /** Binder thread: reads the cached NDEF message (no extra I/O) and the UID. */
    private fun onTag(tag: Tag) {
        val message: NdefMessage? = runCatching { Ndef.get(tag)?.cachedNdefMessage }.getOrNull()
        val records = message?.records.orEmpty().map { NfcFormat.Record(it.tnf.toInt(), it.type, it.payload) }
        val text = NfcFormat.format(tag.id, records, settings)
        activity.runOnUiThread {
            if (text == null) {
                Log.i(TAG, "NFC tag without the data asked for (${settings.read.key})")
                onEmpty()
                return@runOnUiThread
            }
            feedback()
            onScan(ScanResult(text, Symbologies.NFC, SOURCE))
        }
    }

    private fun feedback() {
        if (settings.beep) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }
        if (settings.vibrate) vibrate(activity)
    }

    @SuppressLint("MissingPermission") // VIBRATE is in the manifest.
    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    companion object {
        /** Scan source for the page (`source: "nfc"`). */
        const val SOURCE = "nfc"
        private const val TAG = "NfcReader"
        private const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        private const val PRESENCE_CHECK_MS = 250
        private const val BEEP_MS = 120
        private const val BEEP_VOLUME = 80
        private const val VIBRATION_MS = 60L

        fun hasNfc(context: Context) = NfcAdapter.getDefaultAdapter(context) != null

        fun isOn(context: Context) = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    }
}
