package de.ictbaden.industrialkioskbrowser.web

import de.ictbaden.industrialkioskbrowser.config.ConfigException
import de.ictbaden.industrialkioskbrowser.config.ConfigJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Date

/** Fixtures in src/test/resources/certs: a company CA, a server certificate it signed, a self-signed one. */
class CustomTrustTest {

    private fun text(name: String) = File("src/test/resources/certs/$name").readText()
    private fun cert(name: String) = CustomTrust.parseCertificates(text(name).toByteArray()).single()

    private val ca = text("ca.pem")
    private val leaf = cert("leaf.pem")
    private val selfSigned = cert("selfsigned.pem")
    private val pin = text("selfsigned.sha256").trim()

    @Test
    fun `server certificate signed by the configured CA is accepted`() {
        assertTrue(CustomTrust.from(listOf(ca), emptyMap()).accepts(leaf))
    }

    @Test
    fun `other certificates are not`() {
        val trust = CustomTrust.from(listOf(ca), emptyMap())
        assertFalse(trust.accepts(selfSigned))
        assertFalse(CustomTrust.from(emptyList(), emptyMap()).accepts(leaf))
        // The CA itself is not a pin for its servers' certificates.
        assertFalse(CustomTrust.from(listOf("sha256:" + "0".repeat(64)), emptyMap()).accepts(leaf))
    }

    @Test
    fun `expired server certificate is refused even with the right CA`() {
        val farFuture = Date(leaf.notAfter.time + 86_400_000L)
        assertFalse(CustomTrust.from(listOf(ca), emptyMap()).accepts(leaf, farFuture))
    }

    @Test
    fun `self-signed device is accepted by its fingerprint, with or without colons`() {
        assertTrue(CustomTrust.from(listOf("sha256:$pin"), emptyMap()).accepts(selfSigned))
        val colons = pin.uppercase().chunked(2).joinToString(":")
        assertTrue(CustomTrust.from(listOf("SHA256:$colons"), emptyMap()).accepts(selfSigned))
        assertEquals(pin, CustomTrust.fingerprint(selfSigned))
    }

    @Test
    fun `broken entries are rejected with their index`() {
        listOf("sha256:abc", "-----BEGIN CERTIFICATE-----\nnope\n-----END CERTIFICATE-----", "res:missing").forEach { bad ->
            try {
                CustomTrust.from(listOf(ca, bad), emptyMap())
                fail("accepted $bad")
            } catch (e: ConfigException) {
                assertTrue(e.message!!, e.message!!.contains("global.trustedCertificates[1]"))
            }
        }
    }

    @Test
    fun `config round trip, also as resource and in the compact export`() {
        val json = JSONObject()
            .put("global", JSONObject().put("trustedCertificates", JSONArray(listOf(ca, "sha256:$pin", "res:company-ca"))))
            .put("resources", JSONObject().put("company-ca", JSONObject().put("text", ca)))
            .put("profiles", JSONArray().put(JSONObject().put("id", "p").put("name", "P").put("startUrl", "https://intranet.example")))
        val config = ConfigJson.parse(json.toString())
        assertEquals(3, config.global.trustedCertificates.size)
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        assertEquals(ConfigJson.write(config), ConfigJson.write(ConfigJson.parse(ConfigJson.writeCompact(config))))
        val trust = CustomTrust.from(config.global.trustedCertificates, config.resources)
        assertTrue(trust.accepts(leaf) && trust.accepts(selfSigned))
    }

    @Test
    fun `pem written by the import parses again`() {
        val again = CustomTrust.parseCertificates(CustomTrust.toPem(leaf).toByteArray()).single()
        assertEquals(leaf, again)
    }
}
