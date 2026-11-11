package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.NdefRecordMode
import de.ictbaden.industrialkioskbrowser.config.NfcRead
import de.ictbaden.industrialkioskbrowser.config.NfcSettings
import de.ictbaden.industrialkioskbrowser.config.UidFormat
import java.math.BigInteger

/**
 * Turns an NFC tag into the text that is entered (`scanner.nfc`, issue #50). Pure Kotlin on plain
 * bytes – no Android classes – so it is tested without a tag.
 */
object NfcFormat {

    /** One NDEF record: TNF, type and payload as Android's `NdefRecord` reports them. */
    class Record(val tnf: Int, val type: ByteArray, val payload: ByteArray)

    private const val TNF_WELL_KNOWN = 1
    private const val TNF_MIME_MEDIA = 2
    private const val TNF_ABSOLUTE_URI = 3

    /** URI identifier codes of the NFC Forum URI record type definition. */
    private val URI_PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
        "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
        "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    /** The UID as upper-case hex (optionally with [separator]) or as unsigned decimal number. */
    fun uid(id: ByteArray, format: UidFormat, reversed: Boolean, separator: String): String {
        val bytes = if (reversed) id.reversedArray() else id
        return when (format) {
            UidFormat.HEX -> bytes.joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }
            UidFormat.DECIMAL -> BigInteger(1, bytes).toString()
        }
    }

    /** Text of a Text, URI or `text/…` MIME record; `null` for every other record. */
    fun recordText(record: Record): String? {
        val type = record.type.toString(Charsets.US_ASCII)
        return when {
            record.tnf == TNF_WELL_KNOWN && type == "T" -> text(record.payload)
            record.tnf == TNF_WELL_KNOWN && type == "U" -> uri(record.payload)
            record.tnf == TNF_ABSOLUTE_URI -> type.ifEmpty { null }
            record.tnf == TNF_MIME_MEDIA && type.startsWith("text/", ignoreCase = true) -> record.payload.toString(Charsets.UTF_8)
            else -> null
        }
    }

    /** Text record: status byte (bit 7 = UTF-16, bits 0–5 = length of the language code), language, text. */
    private fun text(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }

    private fun uri(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    /** NDEF content after [mode]: the first readable record, or all readable records as lines. */
    fun ndef(records: List<Record>, mode: NdefRecordMode): String? {
        val texts = records.mapNotNull(::recordText).filter { it.isNotEmpty() }
        if (texts.isEmpty()) return null
        return when (mode) {
            NdefRecordMode.FIRST -> texts.first()
            NdefRecordMode.ALL_LINES -> texts.joinToString("\n")
        }
    }

    /** What is entered for a tag, or `null` when the tag has nothing that [settings] asks for. */
    fun format(id: ByteArray?, records: List<Record>, settings: NfcSettings): String? {
        val uid = id?.takeIf { it.isNotEmpty() }?.let { uid(it, settings.uidFormat, settings.uidReversed, settings.uidSeparator) }
        return when (settings.read) {
            NfcRead.UID -> uid
            NfcRead.NDEF -> ndef(records, settings.ndefRecord)
            NfcRead.NDEF_OR_UID -> ndef(records, settings.ndefRecord) ?: uid
        }
    }
}
