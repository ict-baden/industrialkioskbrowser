package de.ictbaden.industrialkioskbrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateErrorScopeTest {
    private val page = "https://wms.example.com/app/start?x=1"

    @Test
    fun `an error of the page itself concerns the page`() {
        assertTrue(CertificateErrorScope.concernsPage(page, page))
        assertTrue(CertificateErrorScope.concernsPage("$page#top", page))
        assertTrue(CertificateErrorScope.concernsPage("https://wms.example.com/", "https://wms.example.com"))
    }

    @Test
    fun `a resource on the same host concerns the page`() {
        assertTrue(CertificateErrorScope.concernsPage("https://wms.example.com/img/logo.png", page))
        assertTrue(CertificateErrorScope.concernsPage("https://WMS.example.com:8443/api", page))
    }

    @Test
    fun `a resource on another host does not replace the page`() {
        assertFalse(CertificateErrorScope.concernsPage("https://self-signed.badssl.com/", page))
        assertFalse(CertificateErrorScope.concernsPage("https://cdn.example.com/lib.js", page))
        assertFalse(CertificateErrorScope.concernsPage("https://example.com/pixel.gif", page)) // parent domain is another host
        assertFalse(CertificateErrorScope.concernsPage("https://wms.example.com.evil.net/x", page))
    }

    @Test
    fun `unknown addresses count for the page`() {
        assertTrue(CertificateErrorScope.concernsPage(null, page))
        assertTrue(CertificateErrorScope.concernsPage("", page))
        assertTrue(CertificateErrorScope.concernsPage("https://cdn.example.com/lib.js", null))
        assertTrue(CertificateErrorScope.concernsPage("not a url", page))
    }
}
