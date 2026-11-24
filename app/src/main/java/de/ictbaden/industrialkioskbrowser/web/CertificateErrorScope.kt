package de.ictbaden.industrialkioskbrowser.web

/**
 * `onReceivedSslError` fires for every resource, not only for the page itself. An image, a script
 * or an iframe with a bad certificate must not replace the whole page with the error page: the
 * resource is cancelled and the page stays.
 */
object CertificateErrorScope {
    /**
     * True when the certificate error concerns the page that is being loaded: [errorUrl] is the
     * navigation the WebView last started for the main frame, or it is on the same host (then the
     * page itself would hit the same certificate). When either address is unknown the error counts
     * for the page – showing the error page once too often is better than a page that stays blank
     * without a reason.
     */
    fun concernsPage(errorUrl: String?, navigationUrl: String?): Boolean {
        if (errorUrl.isNullOrBlank() || navigationUrl.isNullOrBlank()) return true
        if (withoutFragment(errorUrl) == withoutFragment(navigationUrl)) return true
        val errorHost = UrlPolicy.hostOf(errorUrl) ?: return true
        val pageHost = UrlPolicy.hostOf(navigationUrl) ?: return true
        return errorHost == pageHost
    }

    private fun withoutFragment(url: String) = url.substringBefore('#').trimEnd('/')
}
