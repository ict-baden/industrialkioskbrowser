package de.ictbaden.industrialkioskbrowser.web

/**
 * Decides whether the page is loaded again when the network comes back. Only while the error page
 * for a connection problem is still on screen: after a certificate or HTTP error another try would
 * give the same result, and once the user has moved on (home, back, another page) nothing may
 * pull them back.
 */
object NetworkRetry {
    fun shouldRetry(failedByNetwork: Boolean, failedUrl: String?, currentUrl: String?): Boolean =
        failedByNetwork && !failedUrl.isNullOrEmpty() && currentUrl != null && currentUrl.startsWith(StatusPage.BASE_URL)
}
