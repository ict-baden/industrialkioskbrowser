package de.ictbaden.industrialkioskbrowser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zxingcpp.BarcodeReader

class SymbologiesTest {

    @Test
    fun `zxing formats map to canonical names`() {
        assertEquals("ean13", Symbologies.fromZxing("EAN_13"))
        assertEquals("ean13", Symbologies.fromZxing("ISBN"))
        assertEquals("qr", Symbologies.fromZxing("QR_CODE"))
        assertEquals("qr", Symbologies.fromZxing("QR_CODE_MODEL_2"))
        assertEquals("microqr", Symbologies.fromZxing("MICRO_QR_CODE"))
        assertEquals("code39", Symbologies.fromZxing("CODE_39_EXT"))
        assertEquals("code39", Symbologies.fromZxing("PZN"))
        assertEquals("databarexpanded", Symbologies.fromZxing("DATA_BAR_EXP_STK"))
        assertEquals("databarlimited", Symbologies.fromZxing("DATA_BAR_LTD"))
        assertEquals("databar", Symbologies.fromZxing("DATA_BAR_OMNI"))
        assertEquals("pdf417", Symbologies.fromZxing("COMPACT_PDF_417"))
        assertEquals("micropdf417", Symbologies.fromZxing("MICRO_PDF_417"))
        assertEquals("itf", Symbologies.fromZxing("ITF_14"))
        assertEquals("aztec", Symbologies.fromZxing("AZTEC_RUNE"))
        assertNull(Symbologies.fromZxing("DX_FILM_EDGE"))
        assertNull(Symbologies.fromZxing(null))
    }

    @Test
    fun `every canonical name has zxing formats that exist`() {
        val known = BarcodeReader.Format.entries.map { it.name }.toSet()
        for (name in Symbologies.BARCODES) {
            val formats = Symbologies.toZxing(listOf(name))
            assertTrue("$name has no zxing format", formats.isNotEmpty())
            assertTrue("$name: $formats not all known", known.containsAll(formats))
            // and they map back to the same name
            formats.forEach { assertEquals(name, Symbologies.fromZxing(it)) }
        }
        assertTrue(Symbologies.toZxing(emptyList()).isEmpty())
    }

    @Test
    fun `ocr and nfc are known for rules but no barcode format`() {
        assertTrue(Symbologies.isKnown("ocr") && Symbologies.isKnown("nfc"))
        assertTrue("ocr" in Symbologies.ALL && "ocr" !in Symbologies.BARCODES)
        assertTrue(Symbologies.toZxing(listOf("ocr", "nfc")).isEmpty())
        assertEquals("nfc", Symbologies.of("nfc"))
        assertEquals("Text (OCR)", Symbologies.displayName("ocr"))
    }

    @Test
    fun `datawedge label types map to canonical names`() {
        assertEquals("ean13", Symbologies.fromDataWedge("LABEL-TYPE-EAN13"))
        assertEquals("ean13", Symbologies.fromDataWedge("EAN13"))
        assertEquals("upce", Symbologies.fromDataWedge("LABEL-TYPE-UPCE1"))
        assertEquals("code128", Symbologies.fromDataWedge("LABEL-TYPE-EAN128"))
        assertEquals("itf", Symbologies.fromDataWedge("LABEL-TYPE-I2OF5"))
        assertEquals("qr", Symbologies.fromDataWedge("LABEL-TYPE-QRCODE"))
        assertEquals("datamatrix", Symbologies.fromDataWedge("LABEL-TYPE-GS1-DATAMATRIX"))
        assertEquals("databarexpanded", Symbologies.fromDataWedge("LABEL-TYPE-GS1-DATABAR-EXP"))
        assertNull(Symbologies.fromDataWedge("LABEL-TYPE-POSTNET"))
    }

    @Test
    fun `of accepts every source`() {
        assertEquals("ean13", Symbologies.of("ean13"))
        assertEquals("ean13", Symbologies.of("EAN_13"))
        assertEquals("ean13", Symbologies.of("EAN13"))
        assertEquals("qr", Symbologies.of("QRCODE"))
        assertNull(Symbologies.of("unknown"))
        assertNull(Symbologies.of(null))
    }
}
