package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAuthPolicyTest {
    private val open = Profile(id = "p", name = "P", startUrl = "https://wms.example.com/start")
    private val restricted = open.copy(allowList = listOf("sso.example.org", "*.corp.local"))

    @Test
    fun `hosts outside the allow list may not ask for credentials`() {
        assertFalse(HttpAuthPolicy.mayAsk("httpbin.org", restricted))
        assertFalse(HttpAuthPolicy.mayAsk("angreifer.intern", restricted))
        assertFalse(HttpAuthPolicy.mayAsk("sso.example.org.evil.net", restricted))
        assertFalse(HttpAuthPolicy.mayAsk("10.0.0.9", restricted))
        assertFalse(HttpAuthPolicy.mayAsk("fd00::9", restricted))
    }

    @Test
    fun `allowed hosts, their subdomains and the start host may ask`() {
        assertTrue(HttpAuthPolicy.mayAsk("sso.example.org", restricted))
        assertTrue(HttpAuthPolicy.mayAsk("files.corp.local", restricted))
        assertTrue(HttpAuthPolicy.mayAsk("wms.example.com", restricted))
        assertTrue(HttpAuthPolicy.mayAsk("SSO.example.org", restricted))
    }

    @Test
    fun `entries and start urls with a port still match their host`() {
        val profile = open.copy(startUrl = "http://192.168.1.20:8080/app", allowList = listOf("https://api.example.org:8443/x"))
        assertTrue(HttpAuthPolicy.mayAsk("192.168.1.20", profile))
        assertTrue(HttpAuthPolicy.mayAsk("api.example.org", profile))
        assertFalse(HttpAuthPolicy.mayAsk("192.168.1.21", profile))

        // The WebView reports IPv6 hosts without brackets.
        val ipv6 = open.copy(startUrl = "http://[fd00::1]:8080/app", allowList = listOf("sso.example.org"))
        assertTrue(HttpAuthPolicy.mayAsk("fd00::1", ipv6))
        assertFalse(HttpAuthPolicy.mayAsk("fd00::2", ipv6))
    }

    @Test
    fun `without an allow list every host may ask as before`() {
        assertTrue(HttpAuthPolicy.mayAsk("httpbin.org", open))
    }
}
