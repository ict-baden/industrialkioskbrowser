package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile
import de.ictbaden.industrialkioskbrowser.web.DownloadPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPolicyTest {
    private val open = Profile(id = "p", name = "P", startUrl = "https://wms.example.com/start")
    private val restricted = open.copy(allowList = listOf("files.example.com"))

    @Test
    fun `downloads from hosts outside the allow list are refused`() {
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("https://evil.example.net/tool.apk", restricted))
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("http://10.0.0.9/report.pdf", restricted))
        // Label boundaries as for navigation: no suffix tricks.
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("https://files.example.com.evil.net/x.pdf", restricted))
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("https://notfiles.example.com/x.pdf", restricted))
    }

    @Test
    fun `user info cannot make a foreign download look allowed`() {
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("https://files.example.com@evil.example.net/x.apk", restricted))
        assertEquals(Decision.NOT_ALLOWED, DownloadPolicy.check("https://files.example.com:443@evil.example.net/?a|b", restricted))
    }

    @Test
    fun `downloads from allowed hosts and the start host start`() {
        assertEquals(Decision.START, DownloadPolicy.check("https://files.example.com/report.pdf", restricted))
        assertEquals(Decision.START, DownloadPolicy.check("https://cdn.files.example.com/report.pdf", restricted))
        assertEquals(Decision.START, DownloadPolicy.check("https://wms.example.com/export.csv", restricted))
        assertEquals(Decision.START, DownloadPolicy.check("HTTPS://FILES.EXAMPLE.COM/report.pdf", restricted))
    }

    @Test
    fun `without an allow list every web download starts`() {
        assertEquals(Decision.START, DownloadPolicy.check("https://anything.test/file.zip", open))
        assertEquals(Decision.START, DownloadPolicy.check("http://10.0.0.5:8080/file.zip", open))
    }

    @Test
    fun `allowDownloads off wins over everything else`() {
        val off = restricted.copy(allowDownloads = false)
        assertEquals(Decision.DISABLED, DownloadPolicy.check("https://files.example.com/report.pdf", off))
        assertEquals(Decision.DISABLED, DownloadPolicy.check("https://evil.example.net/tool.apk", off))
        assertEquals(Decision.DISABLED, DownloadPolicy.check("blob:https://files.example.com/1234", off))
    }

    @Test
    fun `only http and https can be downloaded`() {
        for (url in listOf("blob:https://files.example.com/1234", "data:text/plain,hi", "file:///sdcard/x.pdf", "content://x/y", "about:blank", "")) {
            assertEquals(url, Decision.UNSUPPORTED, DownloadPolicy.check(url, restricted))
            assertEquals(url, Decision.UNSUPPORTED, DownloadPolicy.check(url, open))
        }
    }
}
