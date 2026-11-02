package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    private val open = Profile(id = "p", name = "P", startUrl = "https://wms.example.com/start")
    private val restricted = open.copy(allowList = listOf("*.corp.local", "https://sso.example.org/login"))

    @Test
    fun `unrestricted profile allows any http and https url`() {
        assertTrue(UrlPolicy.isAllowed("https://anything.test/page", open))
        assertTrue(UrlPolicy.isAllowed("http://10.0.0.5:8080/x", open))
    }

    @Test
    fun `non web schemes are never allowed`() {
        assertFalse(UrlPolicy.isAllowed("intent://scan#Intent;end", open))
        assertFalse(UrlPolicy.isAllowed("file:///sdcard/x.html", open))
        assertFalse(UrlPolicy.isAllowed("tel:123", open))
        assertTrue(UrlPolicy.isAllowed("about:blank", restricted))
    }

    @Test
    fun `allow list matches host and subdomains on label boundaries`() {
        assertTrue(UrlPolicy.isAllowed("https://corp.local/", restricted))
        assertTrue(UrlPolicy.isAllowed("https://a.b.corp.local/x?y", restricted))
        assertTrue(UrlPolicy.isAllowed("https://sso.example.org/other", restricted))
        assertFalse(UrlPolicy.isAllowed("https://evilcorp.local/", restricted))
        assertFalse(UrlPolicy.isAllowed("https://corp.local.evil.com/", restricted))
    }

    @Test
    fun `start host and bundled assets are always allowed`() {
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/other", restricted))
        assertTrue(UrlPolicy.isAllowed("https://${UrlPolicy.ASSET_HOST}/assets/demo/index.html", restricted))
        assertFalse(UrlPolicy.isAllowed("https://example.com/", restricted))
    }

    @Test
    fun `normalizes allow list entries`() {
        assertEquals("example.com", UrlPolicy.normalizeEntry(" *.Example.com "))
        assertEquals("example.com", UrlPolicy.normalizeEntry("https://example.com:8443/path"))
        assertEquals("example.com", UrlPolicy.normalizeEntry("example.com/path"))
        assertEquals(null, UrlPolicy.normalizeEntry("  "))
    }

    @Test
    fun `bridge rules cover allowed hosts only`() {
        val rules = UrlPolicy.bridgeOriginRules(restricted)
        assertTrue("https://corp.local" in rules)
        assertTrue("https://*.corp.local" in rules)
        assertTrue("https://wms.example.com" in rules)
        assertTrue("https://${UrlPolicy.ASSET_HOST}" in rules)

        val openRules = UrlPolicy.bridgeOriginRules(open)
        assertEquals(setOf("https://${UrlPolicy.ASSET_HOST}", "https://wms.example.com", "https://*.wms.example.com", "http://wms.example.com", "http://*.wms.example.com"), openRules)
        assertTrue(UrlPolicy.isBridgeOrigin("https://wms.example.com/a", open))
        assertFalse(UrlPolicy.isBridgeOrigin("https://other.test/", open))
    }

    @Test
    fun `ip hosts get exact origin rules`() {
        val profile = open.copy(startUrl = "http://192.168.1.20:8080/app")
        assertEquals(
            setOf("https://${UrlPolicy.ASSET_HOST}", "http://192.168.1.20", "https://192.168.1.20"),
            UrlPolicy.bridgeOriginRules(profile),
        )
    }

    @Test
    fun `user info before the host cannot spoof an allowed host`() {
        val restricted = open.copy(allowList = listOf("erlaubt.de"))
        // Chromium keeps | ^ { } unescaped; java.net.URI rejects them and the fallback must still see the real host.
        for (url in listOf(
            "https://erlaubt.de:443@boese.de/?q=a|b",
            "https://erlaubt.de:443@boese.de/path^x",
            "https://erlaubt.de:443@boese.de/{x}",
            "https://erlaubt.de@boese.de/",
            "https://erlaubt.de@bo_se.de/",
            "http://erlaubt.de:80@[fd00::1]/?a|b",
        )) {
            assertFalse(url, UrlPolicy.isAllowed(url, restricted))
        }
        assertEquals("boese.de", UrlPolicy.hostOf("https://erlaubt.de:443@boese.de/?q=a|b"))
        assertEquals("fd00::1", UrlPolicy.hostOf("http://erlaubt.de:80@[fd00::1]/?a|b"))
        assertTrue(UrlPolicy.isAllowed("https://erlaubt.de/?a|b", restricted))
        assertTrue(UrlPolicy.isAllowed("https://user:pw@erlaubt.de/", restricted))
    }
}
