package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.OcrCharset
import de.ictbaden.industrialkioskbrowser.config.OcrMode
import de.ictbaden.industrialkioskbrowser.config.OcrSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFilterTest {

    @Test
    fun `charsets clean the line`() {
        assertEquals("SN AB 123456", OcrFilter.clean("  SN   AB 123456 ", OcrCharset.ANY))
        assertEquals("1.234,5", OcrFilter.clean("Zähler: 1.234,5 kWh", OcrCharset.DIGITS))
        assertEquals("SNAB123456", OcrFilter.clean("SN: AB-123 456", OcrCharset.ALNUM))
        assertEquals("SNAB123456", OcrFilter.clean("sn: ab-123 456", OcrCharset.UPPER))
    }

    @Test
    fun `length and pattern decide`() {
        val serial = OcrSettings(mode = OcrMode.PATTERN, pattern = "[A-Z]{2}\\d{6}", charset = OcrCharset.UPPER)
        assertTrue(OcrFilter.accepts("AB123456", serial))
        assertFalse(OcrFilter.accepts("AB12345", serial))
        assertFalse(OcrFilter.accepts("", OcrSettings()))
        val short = OcrSettings(minLength = 3, maxLength = 5)
        assertFalse(OcrFilter.accepts("ab", short))
        assertTrue(OcrFilter.accepts("abc", short))
        assertFalse(OcrFilter.accepts("abcdef", short))
    }

    @Test
    fun `candidates keep the order and drop the rest`() {
        val mhd = OcrSettings(pattern = "\\d{2}\\.\\d{2}\\.\\d{4}")
        assertEquals(listOf("31.12.2027"), OcrFilter.candidates(listOf("Mindestens haltbar bis", "31.12.2027", "L 4711"), mhd))
    }

    @Test
    fun `confirmation needs the same text in a row`() {
        val confirmer = OcrFilter.Confirmer(2)
        assertNull(confirmer.offer("AB123456"))
        assertNull(confirmer.offer("AB123458"))
        assertNull(confirmer.offer(null))
        assertNull(confirmer.offer("AB123456"))
        assertEquals("AB123456", confirmer.offer("AB123456"))
        // Starts again after a confirmed text.
        assertNull(confirmer.offer("AB123456"))
        assertEquals("X", OcrFilter.Confirmer(1).offer("X"))
    }

    @Test
    fun `boxes map to the view, the closest line wins, taps hit a line`() {
        // 1000×500 image in a 500×500 view: scale 1, shifted 250 to the left.
        val box = OcrFilter.toViewBox(300, 200, 500, 240, 1000, 500, 500f, 500f)
        assertEquals(AimGeometry.Box(50f, 200f, 250f, 240f), box)
        val frame = AimGeometry.Box(50f, 150f, 450f, 350f)
        val lines = listOf("left" to AimGeometry.Box(60f, 160f, 150f, 180f), "middle" to AimGeometry.Box(200f, 240f, 300f, 260f))
        assertEquals("middle", OcrFilter.closest(lines, frame))
        assertNull(OcrFilter.closest(emptyList(), frame))
        assertEquals("left", OcrFilter.lineAt(lines, 155f, 170f, slop = 10f))
        assertNull(OcrFilter.lineAt(lines, 400f, 400f, slop = 10f))
    }
}
