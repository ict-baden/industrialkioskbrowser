package de.ictbaden.industrialkioskbrowser.web

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusPageTest {

    /** Placeholders are filled without regex (Android's ICU regex rejected the old pattern: every error page crashed). */
    @Test
    fun `placeholders are filled case-insensitively and escaped`() {
        val html = StatusPage.fill(
            "<title>{{title}}</title><p>{{Message}}</p><a>{{URL}}</a><i style=\"color:{{color}}\">",
            title = "Error \$1 <b>",
            message = "a & b",
            url = "http://x/?q=\"'",
            color = "#00796B",
        )
        assertEquals(
            "<title>Error \$1 &lt;b&gt;</title><p>a &amp; b</p><a>http://x/?q=&quot;&#39;</a><i style=\"color:#00796B\">",
            html,
        )
    }
}
