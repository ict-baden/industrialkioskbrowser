package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile

/**
 * Decides whether the sign-in dialog for HTTP authentication may be shown. The WebView asks for
 * every resource, also for an image or a frame from a host the profile could never open – a page
 * could use that to ask the person at the device for the credentials of another system.
 */
object HttpAuthPolicy {
    /**
     * True when [host] is one the profile may load. The request names only the host, so every
     * scheme and every port that the allow list or the start URL mention is tried. Without an allow
     * list every host may ask, as before.
     */
    fun mayAsk(host: String, profile: Profile): Boolean {
        if (profile.allowList.isEmpty()) return true
        val literal = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val ports = (profile.allowList + profile.startUrl).mapNotNull { PORT.find(it.trim())?.groupValues?.get(1) }.distinct()
        val authorities = listOf(literal) + ports.map { "$literal:$it" }
        return authorities.any { UrlPolicy.isAllowed("https://$it/", profile) || UrlPolicy.isAllowed("http://$it/", profile) }
    }

    private val PORT = Regex(":(\\d{1,5})(?:[/?#]|$)")
}
