package de.ictbaden.industrialkioskbrowser.scanner

/**
 * Canonical barcode type names used in the configuration (`scanner.camera.symbologies`,
 * `scanner.data.rules[].symbologies`) – the same names as in Industrial Keyboard. Maps the names
 * of zxing-cpp and of DataWedge (`LABEL-TYPE-…`) onto them.
 */
object Symbologies {

    /** Canonical name → display name (product names, not translated). */
    val NAMES: Map<String, String> = linkedMapOf(
        "aztec" to "Aztec",
        "codabar" to "Codabar",
        "code39" to "Code 39",
        "code93" to "Code 93",
        "code128" to "Code 128 / GS1-128",
        "databar" to "GS1 DataBar",
        "databarexpanded" to "GS1 DataBar Expanded",
        "databarlimited" to "GS1 DataBar Limited",
        "datamatrix" to "Data Matrix",
        "ean8" to "EAN-8",
        "ean13" to "EAN-13",
        "itf" to "Interleaved 2 of 5",
        "maxicode" to "MaxiCode",
        "pdf417" to "PDF417",
        "micropdf417" to "MicroPDF417",
        "qr" to "QR Code",
        "microqr" to "Micro QR",
        "rmqr" to "rMQR",
        "upca" to "UPC-A",
        "upce" to "UPC-E",
    )

    /** Text recognition and NFC: not barcode formats, but usable in `scanner.data.rules[].symbologies`. */
    const val OCR = "ocr"
    const val NFC = "nfc"
    private val PSEUDO: Map<String, String> = linkedMapOf(OCR to "Text (OCR)", NFC to "NFC")

    /** Barcode formats only (camera filter, the barcode type dialog). */
    val BARCODES: List<String> get() = NAMES.keys.toList()

    /** Everything a rule can name: the barcode formats plus [OCR] and [NFC]. */
    val ALL: List<String> get() = BARCODES + PSEUDO.keys

    fun isKnown(name: String) = name in NAMES || name in PSEUDO

    fun displayName(name: String) = NAMES[name] ?: PSEUDO[name] ?: name

    /** zxing-cpp `BarcodeReader.Format` name → canonical name, `null` if unknown. */
    fun fromZxing(format: String?): String? {
        val f = format?.uppercase() ?: return null
        return when {
            f.startsWith("AZTEC") -> "aztec"
            f == "CODABAR" -> "codabar"
            f.startsWith("CODE_39") || f == "PZN" || f == "CODE_32" -> "code39"
            f == "CODE_93" -> "code93"
            f == "CODE_128" -> "code128"
            f.startsWith("DATA_BAR_EXP") -> "databarexpanded"
            f == "DATA_BAR_LTD" -> "databarlimited"
            f.startsWith("DATA_BAR") -> "databar"
            f == "DATA_MATRIX" -> "datamatrix"
            f == "EAN_8" -> "ean8"
            f == "EAN_13" || f == "ISBN" -> "ean13"
            f.startsWith("ITF") -> "itf"
            f == "MAXI_CODE" -> "maxicode"
            f == "MICRO_PDF_417" -> "micropdf417"
            f == "PDF_417" || f == "COMPACT_PDF_417" -> "pdf417"
            f == "MICRO_QR_CODE" -> "microqr"
            f == "RMQR_CODE" -> "rmqr"
            f.startsWith("QR_CODE") -> "qr"
            f == "UPC_A" -> "upca"
            f == "UPC_E" -> "upce"
            else -> null
        }
    }

    /** zxing-cpp format names to read for a set of canonical names; empty = all formats. [OCR]/[NFC] are ignored. */
    fun toZxing(names: Collection<String>): Set<String> = names.flatMapTo(linkedSetOf()) { name ->
        when (name) {
            "aztec" -> listOf("AZTEC")
            "codabar" -> listOf("CODABAR")
            "code39" -> listOf("CODE_39")
            "code93" -> listOf("CODE_93")
            "code128" -> listOf("CODE_128")
            "databar" -> listOf("DATA_BAR_OMNI", "DATA_BAR_STK", "DATA_BAR_STK_OMNI")
            "databarexpanded" -> listOf("DATA_BAR_EXP", "DATA_BAR_EXP_STK")
            "databarlimited" -> listOf("DATA_BAR_LTD")
            "datamatrix" -> listOf("DATA_MATRIX")
            "ean8" -> listOf("EAN_8")
            "ean13" -> listOf("EAN_13")
            "itf" -> listOf("ITF")
            "maxicode" -> listOf("MAXI_CODE")
            "pdf417" -> listOf("PDF_417")
            "micropdf417" -> listOf("MICRO_PDF_417")
            "qr" -> listOf("QR_CODE")
            "microqr" -> listOf("MICRO_QR_CODE")
            "rmqr" -> listOf("RMQR_CODE")
            "upca" -> listOf("UPC_A")
            "upce" -> listOf("UPC_E")
            else -> emptyList()
        }
    }

    /** DataWedge `com.symbol.datawedge.label_type`, with or without `LABEL-TYPE-`. */
    fun fromDataWedge(labelType: String?): String? {
        val t = labelType?.uppercase()?.removePrefix("LABEL-TYPE-") ?: return null
        return when (t) {
            "EAN13" -> "ean13"
            "EAN8" -> "ean8"
            "UPCA" -> "upca"
            "UPCE0", "UPCE1" -> "upce"
            "CODE128", "EAN128", "GS1-128" -> "code128"
            "CODE39", "TRIOPTIC39" -> "code39"
            "CODE93" -> "code93"
            "CODABAR" -> "codabar"
            "I2OF5" -> "itf"
            "QRCODE" -> "qr"
            "MICROQR" -> "microqr"
            "DATAMATRIX", "GS1-DATAMATRIX" -> "datamatrix"
            "PDF417" -> "pdf417"
            "MICROPDF" -> "micropdf417"
            "AZTEC" -> "aztec"
            "MAXICODE" -> "maxicode"
            "GS1-DATABAR" -> "databar"
            "GS1-DATABAR-EXP" -> "databarexpanded"
            "GS1-DATABAR-LIM" -> "databarlimited"
            else -> null
        }
    }

    /**
     * Canonical name of a scan, whatever its source reported: a canonical name, a zxing-cpp format
     * (camera) or a DataWedge label type (Zebra, generic intent).
     */
    fun of(symbology: String?): String? {
        symbology ?: return null
        val lower = symbology.lowercase()
        if (isKnown(lower)) return lower
        return fromZxing(symbology) ?: fromDataWedge(symbology)
    }
}
