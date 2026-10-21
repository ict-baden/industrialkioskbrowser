package de.ictbaden.pforte.web

/**
 * Back navigation that skips error pages: they are history entries of their own, and going
 * back to the address that just failed would only fail again.
 */
object BackNavigation {

    /**
     * Steps (negative) from [current] back to the nearest useful entry of [urls], or `null` when
     * there is none – then the back key opens the menu, so a kiosk without toolbar is never stuck.
     */
    fun stepsBack(urls: List<String?>, current: Int, failedUrl: String?): Int? {
        val here = urls.getOrNull(current)
        val onErrorPage = isStatusPage(here)
        for (index in current - 1 downTo 0) {
            val url = urls[index] ?: continue
            if (isStatusPage(url) || url == here) continue
            if (onErrorPage && url == failedUrl) continue
            return index - current
        }
        return null
    }

    fun isStatusPage(url: String?) = url?.startsWith(StatusPage.BASE_URL) == true
}
