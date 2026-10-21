package de.ictbaden.pforte.config

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 hashes in the form `pbkdf2-sha256$<iterations>$<salt>$<hash>`. */
object PasswordHasher {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 60_000
    private const val KEY_BITS = 256

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        val key = derive(password, salt, ITERATIONS)
        return "$PREFIX\$$ITERATIONS\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(key)}"
    }

    fun verify(password: String, stored: String?): Boolean {
        // Android's PBKDF2 throws on an empty password instead of returning a key.
        if (password.isEmpty()) return false
        val parts = stored?.split('$') ?: return false
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        return try {
            val decoder = Base64.getDecoder()
            val expected = decoder.decode(parts[3])
            MessageDigest.isEqual(expected, derive(password, decoder.decode(parts[2]), iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun isHash(value: String?): Boolean = value?.startsWith("$PREFIX\$") == true

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
