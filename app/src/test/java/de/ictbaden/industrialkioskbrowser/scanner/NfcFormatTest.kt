package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.NdefRecordMode
import de.ictbaden.industrialkioskbrowser.config.NfcRead
import de.ictbaden.industrialkioskbrowser.config.NfcSettings
import de.ictbaden.industrialkioskbrowser.config.UidFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcFormatTest {

    private val id = byteArrayOf(0x04, 0xA2.toByte(), 0x3B, 0x7F)

    private fun textRecord(text: String, language: String = "de", utf16: Boolean = false): NfcFormat.Record {
        val bytes = text.toByteArray(if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
        val status = (if (utf16) 0x80 else 0) or language.length
        return NfcFormat.Record(1, "T".toByteArray(), byteArrayOf(status.toByte()) + language.toByteArray() + bytes)
    }

    private fun uriRecord(code: Int, rest: String) = NfcFormat.Record(1, "U".toByteArray(), byteArrayOf(code.toByte()) + rest.toByteArray())

    @Test
    fun `uid as hex, reversed, with separator, as decimal`() {
        assertEquals("04A23B7F", NfcFormat.uid(id, UidFormat.HEX, reversed = false, separator = ""))
        assertEquals("7F:3B:A2:04", NfcFormat.uid(id, UidFormat.HEX, reversed = true, separator = ":"))
        assertEquals("77740927", NfcFormat.uid(id, UidFormat.DECIMAL, reversed = false, separator = ""))
        // Unsigned: a first byte ≥ 0x80 stays positive.
        assertEquals("4294967295", NfcFormat.uid(ByteArray(4) { 0xFF.toByte() }, UidFormat.DECIMAL, reversed = false, separator = ""))
    }

    @Test
    fun `text, uri and mime records`() {
        assertEquals("Regal 12", NfcFormat.recordText(textRecord("Regal 12")))
        assertEquals("Größe", NfcFormat.recordText(textRecord("Größe", "en", utf16 = true)))
        assertEquals("https://example.com/a", NfcFormat.recordText(uriRecord(4, "example.com/a")))
        assertEquals("tel:+49721", NfcFormat.recordText(uriRecord(5, "+49721")))
        assertEquals("plain", NfcFormat.recordText(NfcFormat.Record(2, "text/plain".toByteArray(), "plain".toByteArray())))
        assertNull(NfcFormat.recordText(NfcFormat.Record(2, "application/octet-stream".toByteArray(), byteArrayOf(1, 2))))
        assertNull(NfcFormat.recordText(NfcFormat.Record(1, "T".toByteArray(), byteArrayOf())))
    }

    @Test
    fun `read mode picks ndef, uid or ndef with fallback`() {
        val records = listOf(textRecord("A-1"), uriRecord(3, "x.de"))
        assertEquals("A-1", NfcFormat.format(id, records, NfcSettings(read = NfcRead.NDEF)))
        assertEquals("A-1\nhttp://x.de", NfcFormat.format(id, records, NfcSettings(read = NfcRead.NDEF, ndefRecord = NdefRecordMode.ALL_LINES)))
        assertEquals("04A23B7F", NfcFormat.format(id, records, NfcSettings(read = NfcRead.UID)))
        assertEquals("04A23B7F", NfcFormat.format(id, emptyList(), NfcSettings(read = NfcRead.NDEF_OR_UID)))
        assertEquals("A-1", NfcFormat.format(id, records, NfcSettings(read = NfcRead.NDEF_OR_UID)))
        assertNull(NfcFormat.format(id, emptyList(), NfcSettings(read = NfcRead.NDEF)))
        assertNull(NfcFormat.format(null, emptyList(), NfcSettings(read = NfcRead.UID)))
    }
}
