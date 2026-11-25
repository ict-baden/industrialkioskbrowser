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
    fun `plain host entries allow every scheme and port as before`() {
        assertEquals(UrlPolicy.Rule("example.com"), UrlPolicy.parseEntry(" *.Example.com "))
        assertEquals(UrlPolicy.Rule("example.com"), UrlPolicy.parseEntry("example.com/path"))
        assertEquals(UrlPolicy.Rule("example.com"), UrlPolicy.parseEntry("example.com."))
        assertEquals(null, UrlPolicy.parseEntry("  "))
        val profile = open.copy(allowList = listOf("corp.local"))
        for (url in listOf("https://corp.local/", "http://corp.local/", "http://a.corp.local:8080/x", "https://corp.local:8443/")) {
            assertTrue(url, UrlPolicy.isAllowed(url, profile))
        }
    }

    @Test
    fun `an entry with a scheme allows only that scheme`() {
        assertEquals(UrlPolicy.Rule("example.com", scheme = "https"), UrlPolicy.parseEntry("HTTPS://Example.com/path?x#y"))
        val profile = open.copy(allowList = listOf("https://sso.example.org/login"))
        assertTrue(UrlPolicy.isAllowed("https://sso.example.org/other", profile))
        assertTrue(UrlPolicy.isAllowed("https://id.sso.example.org/", profile))
        assertTrue(UrlPolicy.isAllowed("https://sso.example.org:8443/", profile)) // no port in the entry: any port
        assertFalse(UrlPolicy.isAllowed("http://sso.example.org/other", profile))
        assertFalse(UrlPolicy.isAllowed("http://sso.example.org:8080/", profile))
        assertFalse(UrlPolicy.isAllowed("HTTP://SSO.EXAMPLE.ORG/", profile))
    }

    @Test
    fun `an entry with a port allows only that port`() {
        assertEquals(UrlPolicy.Rule("example.com", port = 8443), UrlPolicy.parseEntry("example.com:8443"))
        assertEquals(UrlPolicy.Rule("example.com", scheme = "https", port = 8443), UrlPolicy.parseEntry("https://example.com:8443/path"))
        assertEquals(UrlPolicy.Rule("example.com"), UrlPolicy.parseEntry("example.com:*"))
        val anyScheme = open.copy(allowList = listOf("api.example.org:8443"))
        assertTrue(UrlPolicy.isAllowed("https://api.example.org:8443/x", anyScheme))
        assertTrue(UrlPolicy.isAllowed("http://api.example.org:8443/x", anyScheme))
        assertFalse(UrlPolicy.isAllowed("https://api.example.org/x", anyScheme))
        assertFalse(UrlPolicy.isAllowed("https://api.example.org:9000/x", anyScheme))

        val both = open.copy(allowList = listOf("https://api.example.org:8443"))
        assertTrue(UrlPolicy.isAllowed("https://api.example.org:8443/x", both))
        assertFalse(UrlPolicy.isAllowed("http://api.example.org:8443/x", both))
        assertFalse(UrlPolicy.isAllowed("https://api.example.org/x", both))
    }

    @Test
    fun `default ports count as the port of the scheme`() {
        val profile = open.copy(allowList = listOf("https://api.example.org:443", "legacy.example.org:80"))
        assertTrue(UrlPolicy.isAllowed("https://api.example.org/x", profile))
        assertTrue(UrlPolicy.isAllowed("https://api.example.org:443/x", profile))
        assertTrue(UrlPolicy.isAllowed("https://api.example.org:0443/x", profile))
        assertFalse(UrlPolicy.isAllowed("https://api.example.org:8443/x", profile))
        assertTrue(UrlPolicy.isAllowed("http://legacy.example.org/x", profile))
        assertFalse(UrlPolicy.isAllowed("https://legacy.example.org/x", profile)) // 443, not 80
    }

    @Test
    fun `the start url always loads and its host stays open without an entry of its own`() {
        // No entry for wms.example.com: the whole start host is allowed, as before.
        val profile = open.copy(allowList = listOf("https://sso.example.org"))
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/start", profile))
        assertTrue(UrlPolicy.isAllowed("http://wms.example.com:8080/reports", profile))
        assertTrue(UrlPolicy.isAllowed("https://sub.wms.example.com/", profile))
    }

    @Test
    fun `an entry for the start host decides about its scheme and port`() {
        // The downgrade from issue #35: with https in the list, http on the same host is refused.
        val httpsOnly = open.copy(allowList = listOf("https://wms.example.com"))
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/start", httpsOnly))
        assertFalse(UrlPolicy.isAllowed("http://wms.example.com/start", httpsOnly))
        assertFalse(UrlPolicy.isAllowed("http://wms.example.com:8080/x", httpsOnly))

        // A parent-domain entry covers the start host too.
        val parent = open.copy(allowList = listOf("https://example.com"))
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/start", parent))
        assertFalse(UrlPolicy.isAllowed("http://wms.example.com/", parent))

        // An entry that does not match the start URL itself never locks the profile out of its start page.
        val otherPort = open.copy(startUrl = "http://wms.example.com:8080/app", allowList = listOf("https://wms.example.com"))
        assertTrue(UrlPolicy.isAllowed("http://wms.example.com:8080/app/next", otherPort))
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/", otherPort))
        assertFalse(UrlPolicy.isAllowed("http://wms.example.com/", otherPort))
        assertFalse(UrlPolicy.isAllowed("http://wms.example.com:9090/", otherPort))
    }

    @Test
    fun `entries that cannot be read allow nothing`() {
        for (entry in listOf("example.com:abc", "example.com:99999", "example.com:0", "https://", "[fd00::1", "[fd00::1]x", ":8080", "*.")) {
            assertEquals(entry, null, UrlPolicy.parseEntry(entry))
        }
        // A list of unreadable entries is still a list: only the start host is left, not every host.
        val profile = open.copy(allowList = listOf("example.com:abc"))
        assertTrue(UrlPolicy.isAllowed("https://wms.example.com/x", profile))
        assertFalse(UrlPolicy.isAllowed("https://example.com/", profile))
        assertFalse(UrlPolicy.isAllowed("https://anything.test/", profile))
        // A scheme the browser never loads matches nothing.
        assertFalse(UrlPolicy.isAllowed("https://files.example.org/", open.copy(allowList = listOf("ftp://files.example.org"))))
    }

    @Test
    fun `ipv6 entries with and without port`() {
        assertEquals(UrlPolicy.Rule("fd00::1"), UrlPolicy.parseEntry("[FD00::1]"))
        assertEquals(UrlPolicy.Rule("fd00::1"), UrlPolicy.parseEntry("fd00::1"))
        assertEquals(UrlPolicy.Rule("fd00::1", port = 8080), UrlPolicy.parseEntry("[fd00::1]:8080"))
        assertEquals(UrlPolicy.Rule("fd00::1", scheme = "http", port = 8080), UrlPolicy.parseEntry("http://[fd00::1]:8080/app"))
        val profile = open.copy(allowList = listOf("http://[fd00::1]:8080"))
        assertTrue(UrlPolicy.isAllowed("http://[fd00::1]:8080/x", profile))
        assertFalse(UrlPolicy.isAllowed("http://[fd00::1]/x", profile))
        assertFalse(UrlPolicy.isAllowed("https://[fd00::1]:8080/x", profile))
        assertFalse(UrlPolicy.isAllowed("http://[fd00::2]:8080/x", profile))
    }

    @Test
    fun `ip addresses match exactly, not as a suffix`() {
        val profile = open.copy(allowList = listOf("1.20", "10.0.0.5"))
        assertTrue(UrlPolicy.isAllowed("http://10.0.0.5:8080/", profile))
        assertFalse(UrlPolicy.isAllowed("http://192.168.1.20/", profile))
        assertFalse(UrlPolicy.isAllowed("http://110.0.0.5/", profile))
    }

    @Test
    fun `urls chromium reads differently from java are judged like chromium does`() {
        val profile = open.copy(allowList = listOf("erlaubt.de"))
        // Chromium turns the backslash into a slash: the host is boese.de, "@erlaubt.de" is path.
        assertFalse(UrlPolicy.isAllowed("https://boese.de\\@erlaubt.de/", profile))
        assertEquals("boese.de", UrlPolicy.hostOf("https://boese.de\\@erlaubt.de/"))
        // A port that is no number is no URL at all.
        assertFalse(UrlPolicy.isAllowed("https://erlaubt.de:80a/?a|b", profile))
        assertFalse(UrlPolicy.isAllowed("https://erlaubt.de:999999/?a|b", profile))
        // The port survives the fallback parser (java.net.URI rejects the "|").
        val port = open.copy(allowList = listOf("erlaubt.de:8443"))
        assertTrue(UrlPolicy.isAllowed("https://erlaubt.de:8443/?a|b", port))
        assertFalse(UrlPolicy.isAllowed("https://erlaubt.de/?a|b", port))
        assertFalse(UrlPolicy.isAllowed("https://erlaubt.de:8443@boese.de/?a|b", port))
        assertFalse(UrlPolicy.isAllowed("https://x@erlaubt.de:9000/?a|b", port))
    }

    @Test
    fun `the blocked message names scheme and port when the host itself is allowed`() {
        val profile = open.copy(allowList = listOf("https://wms.example.com", "api.example.org:8443"))
        assertEquals("example.net", UrlPolicy.describeBlocked("https://example.net/x", profile))
        assertEquals("http://wms.example.com", UrlPolicy.describeBlocked("http://wms.example.com/x?y", profile))
        assertEquals("https://api.example.org", UrlPolicy.describeBlocked("https://api.example.org/x", profile))
        assertEquals("http://api.example.org:9000", UrlPolicy.describeBlocked("http://api.example.org:9000/", profile))
        assertEquals("example.net", UrlPolicy.describeBlocked("https://example.net/", open))
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
            setOf("https://${UrlPolicy.ASSET_HOST}", "http://192.168.1.20", "https://192.168.1.20", "http://192.168.1.20:8080"),
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

    @Test
    fun `ipv6 hosts keep their brackets in origin rules`() {
        val profile = open.copy(startUrl = "http://[fd00::1]:8080/app")
        assertEquals(
            setOf("https://${UrlPolicy.ASSET_HOST}", "http://[fd00::1]", "https://[fd00::1]", "http://[fd00::1]:8080"),
            UrlPolicy.bridgeOriginRules(profile),
        )
        assertTrue(UrlPolicy.isBridgeOrigin("http://[fd00::1]:8080/x", profile))
    }

    @Test
    fun `bridge rules follow the scheme and port of the entries`() {
        val profile = open.copy(allowList = listOf("https://wms.example.com", "api.example.org:8443", "http://10.0.0.5:8080", "ftp://files.example.org"))
        assertEquals(
            setOf(
                "https://${UrlPolicy.ASSET_HOST}",
                "https://wms.example.com", "https://*.wms.example.com",
                "https://api.example.org:8443", "https://*.api.example.org:8443",
                "http://api.example.org:8443", "http://*.api.example.org:8443",
                "http://10.0.0.5:8080",
            ),
            UrlPolicy.bridgeOriginRules(profile),
        )
        assertTrue(UrlPolicy.isBridgeOrigin("https://wms.example.com/a", profile))
        assertFalse(UrlPolicy.isBridgeOrigin("http://wms.example.com/a", profile))
        assertTrue(UrlPolicy.isBridgeOrigin("http://api.example.org:8443/", profile))
        assertFalse(UrlPolicy.isBridgeOrigin("https://api.example.org/", profile))
        assertFalse(UrlPolicy.isBridgeOrigin("https://files.example.org/", profile))
    }

    @Test
    fun `a start url outside its own entry gets an exact bridge rule`() {
        val profile = open.copy(startUrl = "http://wms.example.com:8080/app", allowList = listOf("https://wms.example.com"))
        val rules = UrlPolicy.bridgeOriginRules(profile)
        assertTrue("http://wms.example.com:8080" in rules)
        assertTrue("https://wms.example.com" in rules)
        assertFalse("http://wms.example.com" in rules)
    }
}
