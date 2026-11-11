package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigException
import de.ictbaden.industrialkioskbrowser.config.Resource
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

/**
 * Company CAs and pinned server certificates from `global.trustedCertificates`, for devices where
 * the CA cannot be put into the Android certificate store (no EMM, Android 11+). The WebView only
 * reports the server certificate itself, so a trusted entry must be the CA that signed it (root or
 * intermediate) or the server certificate's SHA-256 fingerprint (self-signed devices).
 *
 * Entries: PEM text, `res:<name>` (PEM or DER resource) or `sha256:<64 hex digits>`.
 */
class CustomTrust(val authorities: List<X509Certificate>, val pins: Set<String>) {

    val isEmpty: Boolean get() = authorities.isEmpty() && pins.isEmpty()

    /** True when [server] is pinned, or valid at [now] and signed by one of the authorities. */
    fun accepts(server: X509Certificate, now: Date = Date()): Boolean {
        if (fingerprint(server) in pins) return true
        if (!validAt(server, now)) return false
        return authorities.any { ca ->
            ca.subjectX500Principal == server.issuerX500Principal &&
                validAt(ca, now) &&
                runCatching { server.verify(ca.publicKey) }.isSuccess
        }
    }

    companion object {
        const val PIN_PREFIX = "sha256:"
        private val PIN = Regex("[0-9a-f]{64}")

        /** Parses every entry; throws [ConfigException] naming the entry that is broken. */
        fun from(entries: List<String>, resources: Map<String, Resource>): CustomTrust {
            val authorities = mutableListOf<X509Certificate>()
            val pins = mutableSetOf<String>()
            entries.forEachIndexed { i, raw ->
                val where = "global.trustedCertificates[$i]"
                val entry = raw.trim()
                when {
                    entry.startsWith(PIN_PREFIX, ignoreCase = true) -> {
                        val hex = entry.substring(PIN_PREFIX.length).replace(":", "").lowercase()
                        if (!PIN.matches(hex)) throw ConfigException("$where: sha256 fingerprint must have 64 hex digits")
                        pins += hex
                    }
                    entry.startsWith(AppConfig.RES_PREFIX) -> {
                        val resource = resources[entry.removePrefix(AppConfig.RES_PREFIX)]
                            ?: throw ConfigException("$where: unknown resource $entry")
                        authorities += parseCertificates(resource.bytes, where)
                    }
                    else -> authorities += parseCertificates(entry.toByteArray(Charsets.US_ASCII), where)
                }
            }
            return CustomTrust(authorities, pins)
        }

        /** PEM (one or more certificates) or DER. */
        fun parseCertificates(bytes: ByteArray, where: String = "certificate"): List<X509Certificate> {
            val factory = CertificateFactory.getInstance("X.509")
            val certificates = runCatching {
                factory.generateCertificates(ByteArrayInputStream(bytes)).filterIsInstance<X509Certificate>()
            }.getOrNull()
            if (certificates.isNullOrEmpty()) throw ConfigException("$where: not a PEM or DER X.509 certificate")
            return certificates
        }

        /** SHA-256 over the DER encoding, lower-case hex without separators. */
        fun fingerprint(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

        /** PEM text of a certificate, as the admin screen stores an imported file. */
        fun toPem(certificate: X509Certificate): String =
            "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded) +
                "\n-----END CERTIFICATE-----\n"

        private fun validAt(certificate: X509Certificate, now: Date) =
            runCatching { certificate.checkValidity(now) }.isSuccess
    }
}
