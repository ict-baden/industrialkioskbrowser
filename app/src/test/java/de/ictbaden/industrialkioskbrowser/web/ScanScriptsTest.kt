package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.ScanTerminator
import de.ictbaden.industrialkioskbrowser.scanner.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanScriptsTest {

    @Test
    fun `javascript templates get a quoted string literal`() {
        assertEquals(
            "javascript:lookup(\"A\\\"B\")",
            ScanScripts.applyTemplate("javascript:lookup(%s)", "A\"B"),
        )
    }

    @Test
    fun `url templates get url encoded data`() {
        assertEquals(
            "https://wms.test/item?code=A%26B+1",
            ScanScripts.applyTemplate("https://wms.test/item?code=%s", "A&B 1"),
        )
    }

    @Test
    fun `scan data is embedded as json`() {
        val script = ScanScripts.deliver(ScanResult("</script>'\n", "CODE128", "test", 1), injectIntoFocusedField = true)
        assertTrue(script.contains("\"data\":\"<\\/script>'\\n\""))
        assertTrue(script.trimEnd().endsWith(", true)"))
    }

    @Test
    fun `decodes evaluateJavascript string results`() {
        assertEquals("inserted", ScanScripts.decodeStringResult("\"inserted\""))
        assertNull(ScanScripts.decodeStringResult("null"))
        assertNull(ScanScripts.decodeStringResult(null))
    }

    @Test
    fun `parses collected meta tags`() {
        val tags = ScanScripts.parseMetaTags("\"[[\\\"scanner\\\",\\\"Disable\\\"],[\\\"scannernavigate\\\",\\\"javascript:f(%s)\\\"]]\"")
        assertEquals(listOf("scanner" to "Disable", "scannernavigate" to "javascript:f(%s)"), tags)
    }

    @Test
    fun `meta tags configure page policy`() {
        val policy = PagePolicy.fromMetaTags(listOf("Scanner" to "Enable, AutoTabAndEnter", "ScannerNavigate" to "https://x/%s"))
        assertEquals(true, policy.scannerEnabled)
        assertEquals(ScanTerminator.TAB_ENTER, policy.terminator)
        assertEquals("https://x/%s", policy.navigateTemplate)

        assertEquals(false, PagePolicy.fromMetaTags(listOf("scanner" to "Suspend")).scannerEnabled)
        assertEquals(ScanTerminator.NONE, PagePolicy.fromMetaTags(listOf("scanner" to "NoAuto")).terminator)
        assertEquals(PagePolicy.NONE, PagePolicy.fromMetaTags(listOf("viewport" to "width=device-width")))
    }

    @Test
    fun `navigation targets ignore leading spaces and resolve relative addresses`() {
        assertEquals(
            ScanScripts.Target.Script("lookup(\"4006\")"),
            ScanScripts.navigationTarget("  javascript:lookup(%s)", "4006", "https://wms.example.com/a"),
        )
        assertEquals(
            ScanScripts.Target.Address("https://wms.example.com/item?code=A%2BB"),
            ScanScripts.navigationTarget(" /item?code=%s", "A+B", "https://wms.example.com/list/x"),
        )
        assertEquals(
            ScanScripts.Target.Address("https://other.example.org/%3Cx%3E"),
            ScanScripts.navigationTarget("https://other.example.org/%s", "<x>", null),
        )
    }
}
