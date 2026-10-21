package de.ictbaden.pforte.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackNavigationTest {

    private val status = StatusPage.BASE_URL

    @Test
    fun `normal pages go back one step`() {
        assertEquals(-1, BackNavigation.stepsBack(listOf("https://a/1", "https://a/2"), 1, null))
    }

    @Test
    fun `error page goes back past the failed address`() {
        val urls = listOf("https://a/1", "https://a/broken", status)
        assertEquals(-2, BackNavigation.stepsBack(urls, 2, "https://a/broken"))
    }

    @Test
    fun `error page as first page has nowhere to go`() {
        assertNull(BackNavigation.stepsBack(listOf("https://a/start", status), 1, "https://a/start"))
        assertNull(BackNavigation.stepsBack(listOf(status), 0, null))
    }

    @Test
    fun `pages after a retry skip the old error page`() {
        val urls = listOf("https://a/1", status, "https://a/2")
        assertEquals(-2, BackNavigation.stepsBack(urls, 2, "https://a/2"))
    }

    @Test
    fun `first page has no back step`() {
        assertNull(BackNavigation.stepsBack(listOf("https://a/1"), 0, null))
        assertNull(BackNavigation.stepsBack(emptyList(), -1, null))
    }
}
