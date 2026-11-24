package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.Profile
import java.net.URI

/** Navigation allow list and JS-bridge origin rules for a [Profile]. */
object UrlPolicy {
    /** Host under which bundled assets (demo, status pages) are served. */
    const val ASSET_HOST = "appassets.androidplatform.net"

    /**
     * One allow-list entry: a host with its subdomains, limited to [scheme] and [port] when the
     * entry names them (`https://example.com`, `example.com:8443`). `null` means "any".
     */
    data class Rule(val host: String, val scheme: String? = null, val port: Int? = null) {
        /** The entry is about this host (or a parent domain of it), whatever scheme and port it asks for. */
        fun covers(host: String) = host == this.host || (!isIpAddress(host) && host.endsWith(".${this.host}"))

        internal fun matches(address: Address) =
            covers(address.host) && (scheme == null || scheme == address.scheme) && (port == null || port == address.port)
    }

    /** Scheme, host and effective port (explicit, or the default of the scheme) of a URL. */
    internal data class Address(val scheme: String, val host: String, val port: Int)

    fun hostOf(url: String): String? = parse(url)?.host

    /**
     * `java.net.URI` first; it rejects URLs Chromium accepts (`|`, `^`, `{`, `}` stay unescaped),
     * then [FALLBACK] reads scheme, host and port. The user info before `@` is skipped:
     * `https://allowed.de:443@evil.de/` would otherwise look like `allowed.de`. Chromium reads `\`
     * as `/` in http and https URLs, so `https://evil.de\@allowed.de/` is `evil.de` there – the
     * fallback does the same before it looks for the host.
     */
    internal fun parse(url: String): Address? {
        val trimmed = url.trim()
        val uri = try {
            URI(trimmed).takeIf { it.host != null }
        } catch (_: Exception) {
            null
        }
        val scheme: String
        val rawHost: String
        val explicitPort: Int?
        if (uri != null) {
            scheme = uri.scheme?.lowercase() ?: return null
            rawHost = uri.host
            explicitPort = uri.port.takeIf { it >= 0 }
        } else {
            val match = FALLBACK.find(trimmed.replace('\\', '/')) ?: return null
            scheme = match.groupValues[1].lowercase()
            rawHost = match.groupValues[2]
            val portText = match.groups[3]?.value
            // "host:80a" is no URL for Chromium; never guess a port for it.
            explicitPort = if (portText.isNullOrEmpty()) null else parsePort(portText) ?: return null
        }
        val host = rawHost.lowercase().trim('.', '[', ']').ifEmpty { return null }
        return Address(scheme, host, explicitPort ?: defaultPort(scheme))
    }

    private val FALLBACK = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://(?:[^/?#]*@)?(\\[[^\\]]*]|[^/:?#@]+)(?::([^/?#]*))?")

    private val ENTRY_SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://(.*)$")

    private fun defaultPort(scheme: String) = when (scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }

    private fun parsePort(text: String): Int? =
        text.takeIf { it.length <= 5 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it in 1..65535 }

    /**
     * Reads an allow-list entry. `example.com` and `*.example.com` allow the host with any scheme
     * and port, as before. A scheme (`https://example.com`) or a port (`example.com:8443`,
     * `https://example.com:8443`) limits the entry to it; a path is ignored. Entries that cannot
     * be read (no host, a port that is no number) are dropped, they allow nothing.
     */
    fun parseEntry(entry: String): Rule? {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = ENTRY_SCHEME.find(trimmed)
        val scheme = withScheme?.groupValues?.get(1)?.lowercase()
        val authority = (withScheme?.groupValues?.get(2) ?: trimmed).replace('\\', '/')
            .substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val hostText: String
        val portText: String
        when {
            authority.startsWith("[") -> { // IPv6 literal, optionally with a port: [fd00::1]:8080
                val rest = authority.substringAfter(']', missingDelimiterValue = "?")
                if (rest.isNotEmpty() && !rest.startsWith(":")) return null
                hostText = authority.substringBefore(']').removePrefix("[")
                portText = rest.removePrefix(":")
            }
            authority.count { it == ':' } > 1 -> { // IPv6 literal without brackets cannot carry a port
                hostText = authority
                portText = ""
            }
            else -> {
                hostText = authority.substringBefore(':')
                portText = authority.substringAfter(':', "")
            }
        }
        val port = if (portText.isEmpty() || portText == "*") null else parsePort(portText) ?: return null
        val host = hostText.lowercase().removePrefix("*.").trim('.').ifEmpty { return null }
        return Rule(host, scheme, port)
    }

    /**
     * What the profile may load; empty when navigation is unrestricted. The start URL always loads.
     * Its host is allowed as a whole (any scheme and port, with subdomains) unless the allow list
     * has an entry of its own for that host – then that entry decides, so `https://wms.example.com`
     * in the list keeps `http://wms.example.com` out even when the profile starts there.
     */
    fun rules(profile: Profile): List<Rule> {
        if (profile.allowList.isEmpty()) return emptyList()
        val entries = profile.allowList.mapNotNull(::parseEntry).distinct()
        val start = parse(profile.startUrl) ?: return entries
        return when {
            entries.none { it.covers(start.host) } -> entries + Rule(start.host)
            entries.none { it.matches(start) } -> entries + Rule(start.host, start.scheme, start.port)
            else -> entries
        }
    }

    fun isAllowed(url: String, profile: Profile): Boolean {
        if (url.equals("about:blank", ignoreCase = true)) return true
        val address = parse(url) ?: return false
        if (address.scheme != "http" && address.scheme != "https") return false
        if (address.host == ASSET_HOST) return true
        // Decided by the configured list, not by what could be read from it: a list of unreadable entries allows nothing.
        if (profile.allowList.isEmpty()) return true
        return rules(profile).any { it.matches(address) }
    }

    /**
     * Text for the "page not allowed" message: the host, or scheme, host and port when the host is
     * on the allow list and only the scheme or the port is not – otherwise the message would name
     * a host the administrator has just allowed.
     */
    fun describeBlocked(url: String, profile: Profile): String {
        val address = parse(url) ?: return url
        if (rules(profile).none { it.covers(address.host) }) return address.host
        val host = if (address.host.contains(':')) "[${address.host}]" else address.host
        val port = if (address.port == defaultPort(address.scheme)) "" else ":${address.port}"
        return "${address.scheme}://$host$port"
    }

    /** The rules the JS bridge follows: the allow list, or the start URL's host when navigation is unrestricted. */
    private fun bridgeRules(profile: Profile): List<Rule> =
        rules(profile).ifEmpty { listOfNotNull(hostOf(profile.startUrl)?.let { Rule(it) }) }

    /**
     * Origin rules for the native JS bridge. The bridge is only exposed to the allow-listed hosts
     * (with the scheme and port limits of their entries), or to the start URL's host when
     * navigation is unrestricted.
     */
    fun bridgeOriginRules(profile: Profile): Set<String> {
        val rules = linkedSetOf("https://$ASSET_HOST")
        for (rule in bridgeRules(profile)) {
            val schemes = when (rule.scheme) {
                null -> listOf("https", "http")
                "https", "http" -> listOf(rule.scheme)
                else -> continue // matches no page; an origin rule for it would be invalid
            }
            val port = rule.port?.let { ":$it" } ?: ""
            for (scheme in schemes) {
                if (isIpAddress(rule.host)) {
                    // Origin rules need the brackets of IPv6 addresses back ("http://[fd00::1]").
                    val literal = if (rule.host.contains(':')) "[${rule.host}]" else rule.host
                    rules += "$scheme://$literal$port"
                } else {
                    rules += "$scheme://${rule.host}$port"
                    rules += "$scheme://*.${rule.host}$port"
                }
            }
        }
        // An origin rule without a port only matches the standard port of its scheme (WebViewCompat
        // documentation), so a start URL on another port gets a rule of its own.
        parse(profile.startUrl)?.let { start ->
            if ((start.scheme == "http" || start.scheme == "https") && start.port != defaultPort(start.scheme)) {
                val literal = if (start.host.contains(':')) "[${start.host}]" else start.host
                rules += "${start.scheme}://$literal:${start.port}"
            }
        }
        return rules
    }

    fun isBridgeOrigin(url: String, profile: Profile): Boolean {
        val address = parse(url) ?: return false
        if (address.host == ASSET_HOST) return true
        if (address.scheme != "http" && address.scheme != "https") return false
        return bridgeRules(profile).any { it.matches(address) }
    }

    private fun isIpAddress(host: String) = host.contains(':') || host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))
}
