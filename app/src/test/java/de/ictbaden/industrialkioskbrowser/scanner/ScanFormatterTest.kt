package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.Replacement
import de.ictbaden.industrialkioskbrowser.config.RuleAction
import de.ictbaden.industrialkioskbrowser.config.ScanDataSettings
import de.ictbaden.industrialkioskbrowser.config.ScanRule
import de.ictbaden.industrialkioskbrowser.config.TextCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanFormatterTest {

    private fun format(text: String, settings: ScanDataSettings, symbology: String? = null) = ScanFormatter.format(text, symbology, settings)

    @Test
    fun `defaults only trim`() {
        assertEquals("4006381333931", format("  4006381333931\r\n", ScanDataSettings()))
        assertEquals(" a ", format(" a ", ScanDataSettings(trim = false)))
    }

    @Test
    fun `trim keeps the group separator`() {
        assertEquals("\u001D0112345", format("\u001D0112345\n", ScanDataSettings()))
    }

    @Test
    fun `prefix and suffix are plain text around the code`() {
        assertEquals("#ABC;", format("ABC", ScanDataSettings(prefix = "#", suffix = ";")))
        assertEquals("ABC", format("ABC", ScanDataSettings(suffix = null)))
    }

    @Test
    fun `steps run in the documented order`() {
        // strip → remove spaces → replace → case → prefix/suffix
        val settings = ScanDataSettings(
            stripLeading = 2,
            stripTrailing = 1,
            removeSpaces = true,
            replace = listOf(Replacement("b", "x")),
            case = TextCase.UPPER,
            prefix = "p:",
        )
        assertEquals("p:AXC", format("]Ca b cZ", settings))
    }

    @Test
    fun `regex replacement with groups`() {
        val settings = ScanDataSettings(replace = listOf(Replacement("^0+(\\d+)$", "\$1", regex = true)))
        assertEquals("1234", format("0001234", settings))
    }

    @Test
    fun `gs replacement`() {
        assertEquals("01123|10ABC", format("01123\u001D10ABC", ScanDataSettings(gsReplacement = "|")))
        assertEquals("0112310ABC", format("01123\u001D10ABC", ScanDataSettings(gsReplacement = "")))
        assertEquals("01123\u001D10ABC", format("01123\u001D10ABC", ScanDataSettings()))
    }

    @Test
    fun `strip GTIN check digit with a rule for EAN-13 only`() {
        val settings = ScanDataSettings(rules = listOf(ScanRule(symbologies = listOf("ean13"), stripTrailing = 1)))
        assertEquals("400638133393", format("4006381333931", settings, "ean13"))
        assertEquals("4006381333931", format("4006381333931", settings, "code128"))
    }

    @Test
    fun `drop everything but EAN-13`() {
        val settings = ScanDataSettings(rules = listOf(ScanRule(symbologies = listOf("ean13"))), unmatched = RuleAction.DROP)
        assertEquals("4006381333931", format("4006381333931", settings, "ean13"))
        assertNull(format("ABC-123", settings, "code128"))
        assertNull(format("ABC-123", settings, null))
    }

    @Test
    fun `first matching rule wins and drop rules drop`() {
        val settings = ScanDataSettings(
            prefix = "D:",
            rules = listOf(
                ScanRule(startsWith = "X", action = RuleAction.DROP),
                ScanRule(minLength = 5, maxLength = 6, prefix = "L:"),
                ScanRule(pattern = "\\d+", prefix = "N:", case = TextCase.LOWER),
            ),
        )
        assertNull(format("X123", settings))
        assertEquals("L:ABCDE", format("ABCDE", settings))
        assertEquals("N:123", format("123", settings))
        // pattern must match the whole text, no rule → defaults
        assertEquals("D:12a", format("12a", settings))
    }

    @Test
    fun `unknown symbology only matches rules without symbologies`() {
        val settings = ScanDataSettings(
            rules = listOf(ScanRule(symbologies = listOf("qr"), prefix = "Q:"), ScanRule(prefix = "A:")),
        )
        assertEquals("A:x", format("x", settings, null))
        assertEquals("Q:x", format("x", settings, "qr"))
    }

    @Test
    fun `rules see the trimmed text`() {
        val settings = ScanDataSettings(rules = listOf(ScanRule(pattern = "\\d{3}", prefix = "#")))
        assertEquals("#123", format(" 123 \n", settings))
    }

    @Test
    fun `nothing left means dropped`() {
        assertNull(format("12", ScanDataSettings(stripLeading = 1, stripTrailing = 1)))
        assertNull(format("12", ScanDataSettings(stripLeading = 5)))
        assertNull(format("   ", ScanDataSettings()))
    }

    @Test
    fun `rule overrides default fields`() {
        val settings = ScanDataSettings(
            removeSpaces = true,
            case = TextCase.UPPER,
            replace = listOf(Replacement("a", "b")),
            rules = listOf(ScanRule(removeSpaces = false, case = TextCase.NONE, replace = emptyList())),
        )
        assertEquals("a a", format("a a", settings))
    }
}
