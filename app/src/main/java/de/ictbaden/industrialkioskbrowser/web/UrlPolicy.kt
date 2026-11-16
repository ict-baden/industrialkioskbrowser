package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile
import java.net.URI

/** Navigation allow list and JS-bridge origin rules for a [Profile]. */
object UrlPolicy {
    /** Host under which bundled assets (demo, status pages) are served. */
    const val ASSET_HOST = "appassets.androidplatform.net"

    fun hostOf(url: String): String? {
        val host = try {
            URI(url.trim()).host
        } catch (_: Exception) {
            null
        } ?: FALLBACK_HOST.find(url.trim())?.groupValues?.get(1)
        return host?.lowercase()?.trim('.', '[', ']')?.ifEmpty { null }
    }

    /**
     * Host of a URL that `java.net.URI` rejects (Chromium leaves `|`, `^`, `{`, `}` unescaped, URI
     * does not accept them). The user info before `@` is skipped: `https://allowed.de:443@evil.de/`
     * would otherwise look like `allowed.de`.
     */
    private val FALLBACK_HOST = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#]*@)?(\\[[^\\]]*]|[^/:?#@]+)")

    /** Reduces an allow-list entry (`example.com`, `*.example.com`, `https://example.com/x`) to its host. */
    fun normalizeEntry(entry: String): String? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        val host = if (trimmed.contains("://")) hostOf(trimmed) else trimmed.substringBefore('/').substringBefore(':')
        return host?.lowercase()?.removePrefix("*.")?.trim('.')?.ifEmpty { null }
    }

    /** Hosts the profile may load; empty when navigation is unrestricted. */
    fun allowedHosts(profile: Profile): Set<String> {
        if (profile.allowList.isEmpty()) return emptySet()
        return (profile.allowList.mapNotNull(::normalizeEntry) + listOfNotNull(hostOf(profile.startUrl))).toSet()
    }

    fun isAllowed(url: String, profile: Profile): Boolean {
        val scheme = url.substringBefore(':', "").lowercase()
        if (url.equals("about:blank", ignoreCase = true)) return true
        if (scheme != "http" && scheme != "https") return false
        val host = hostOf(url) ?: return false
        if (host == ASSET_HOST) return true
        val allowed = allowedHosts(profile)
        return allowed.isEmpty() || allowed.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Origin rules for the native JS bridge. The bridge is only exposed to the allow-listed hosts,
     * or to the start URL's host when navigation is unrestricted.
     */
    fun bridgeOriginRules(profile: Profile): Set<String> {
        val hosts = allowedHosts(profile).ifEmpty { setOfNotNull(hostOf(profile.startUrl)) }
        val rules = linkedSetOf("https://$ASSET_HOST")
        for (host in hosts) {
            if (isIpAddress(host)) {
                rules += "http://$host"
                rules += "https://$host"
            } else {
                for (scheme in listOf("https", "http")) {
                    rules += "$scheme://$host"
                    rules += "$scheme://*.$host"
                }
            }
        }
        return rules
    }

    fun isBridgeOrigin(url: String, profile: Profile): Boolean {
        val host = hostOf(url) ?: return false
        if (host == ASSET_HOST) return true
        val hosts = allowedHosts(profile).ifEmpty { setOfNotNull(hostOf(profile.startUrl)) }
        return hosts.any { host == it || host.endsWith(".$it") }
    }

    private fun isIpAddress(host: String) = host.contains(':') || host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))
}
