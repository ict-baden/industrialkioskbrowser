package de.ictbaden.industrialkioskbrowser.admin

import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigCodec
import de.ictbaden.industrialkioskbrowser.config.ConfigJson
import de.ictbaden.industrialkioskbrowser.config.Profile
import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConfigBarcodeTest {

    @Test
    fun `a typical configuration fits with error correction M or better`() {
        val config = AppConfig(profiles = (1..3).map { Profile(id = "p$it", name = "Profile $it", startUrl = "https://example.com/$it") })
        val qr = ConfigBarcode.encode(ConfigCodec.pack(ConfigJson.write(config)))
        assertNotNull(qr)
        // Ecc is declared LOW, MEDIUM, QUARTILE, HIGH.
        assertTrue(qr!!.errorCorrectionLevel.ordinal >= QrCode.Ecc.MEDIUM.ordinal)
    }

    @Test
    fun `falls back to L and then gives up`() {
        // Random base64url text does not compress: 2 800 bytes need a version 38–40 code at L and do not fit at M.
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val random = Random(1)
        fun text(n: Int) = String(CharArray(n) { alphabet[random.nextInt(alphabet.length)] })
        val large = ConfigBarcode.encode(text(2800))
        assertNotNull(large)
        assertEquals(QrCode.Ecc.LOW, large!!.errorCorrectionLevel)
        assertTrue(large.version >= 38)
        assertNull(ConfigBarcode.encode(text(3200)))
    }
}
