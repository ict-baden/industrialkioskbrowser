package de.ictbaden.industrialkioskbrowser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class ConfigCodecTest {

    private fun config(profiles: Int) = AppConfig(
        profiles = (1..profiles).map { Profile(id = "p$it", name = "Profile $it", startUrl = "https://example.com/app/$it") },
    )

    @Test
    fun `short configurations stay plain minified JSON`() {
        val json = ConfigJson.write(config(1))
        val packed = ConfigCodec.pack(json)
        assertFalse(packed.startsWith(ConfigCodec.PREFIX))
        assertFalse(packed.contains("\n"))
        assertEquals(config(1), ConfigJson.parse(packed))
    }

    @Test
    fun `long configurations are packed and parse again`() {
        val original = config(12)
        val json = ConfigJson.write(original)
        val packed = ConfigCodec.pack(json)
        assertTrue(packed.startsWith(ConfigCodec.PREFIX))
        assertTrue("packed ${packed.length} of ${json.length}", packed.length < json.length / 3)
        assertFalse(packed.contains('=') || packed.contains('+') || packed.contains('/'))
        assertEquals(original, ConfigJson.parse(packed))
        assertEquals(original, ConfigJson.parse("  $packed\n"))
    }

    @Test
    fun `plain text passes unpack unchanged`() {
        assertEquals("{\"a\":1}", ConfigCodec.unpack("{\"a\":1}"))
    }

    @Test
    fun `broken payloads are rejected`() {
        val cut = Base64.getUrlEncoder().withoutPadding().encodeToString(deflate("{\"x\":1}".toByteArray()).copyOf(3))
        for (bad in listOf("ICTZ1:%%%", "ICTZ1:AAAA", "ICTZ1:$cut")) {
            try {
                ConfigJson.parse(bad)
                fail("accepted $bad")
            } catch (_: ConfigException) {
                // expected
            }
        }
    }

    @Test
    fun `unpacked size is limited`() {
        val bomb = ByteArray(ConfigCodec.MAX_UNPACKED + 10) { ' '.code.toByte() }
        val payload = ConfigCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflate(bomb))
        try {
            ConfigCodec.unpack(payload)
            fail("accepted more than 1 MB")
        } catch (e: ConfigException) {
            assertTrue(e.message!!.contains("1 MB"))
        }
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val deflater = Deflater(9, true)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        return out.toByteArray()
    }
}
