package de.ictbaden.industrialkioskbrowser.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRetryTest {
    private val failed = "https://wms.example.com/pick"

    @Test
    fun `retries while the connection error page is shown`() {
        assertTrue(NetworkRetry.shouldRetry(true, failed, StatusPage.BASE_URL))
    }

    @Test
    fun `does not retry after a certificate or HTTP error`() {
        assertFalse(NetworkRetry.shouldRetry(false, failed, StatusPage.BASE_URL))
    }

    @Test
    fun `does not pull the user back once another page is shown`() {
        assertFalse(NetworkRetry.shouldRetry(true, failed, "https://wms.example.com/"))
        assertFalse(NetworkRetry.shouldRetry(true, failed, null))
    }

    @Test
    fun `needs an address to load`() {
        assertFalse(NetworkRetry.shouldRetry(true, null, StatusPage.BASE_URL))
        assertFalse(NetworkRetry.shouldRetry(true, "", StatusPage.BASE_URL))
    }
}
