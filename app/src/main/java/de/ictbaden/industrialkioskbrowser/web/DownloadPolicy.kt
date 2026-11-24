package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile

/**
 * Decides whether a download offered by a page may start. Chromium starts `<a download>` and
 * script-triggered downloads without a navigation, so `shouldOverrideUrlLoading` never sees them:
 * the allow list has to be checked here as well.
 */
object DownloadPolicy {
    enum class Decision {
        START,

        /** `allowDownloads` is off. */
        DISABLED,

        /** Not `http`/`https` (`blob:`, `data:` …): the DownloadManager cannot fetch it. */
        UNSUPPORTED,

        /** The file comes from a host outside the profile's allow list. */
        NOT_ALLOWED,
    }

    fun check(url: String, profile: Profile): Decision = when {
        !profile.allowDownloads -> Decision.DISABLED
        !isWebUrl(url) -> Decision.UNSUPPORTED
        !UrlPolicy.isAllowed(url, profile) -> Decision.NOT_ALLOWED
        else -> Decision.START
    }

    private fun isWebUrl(url: String) =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
