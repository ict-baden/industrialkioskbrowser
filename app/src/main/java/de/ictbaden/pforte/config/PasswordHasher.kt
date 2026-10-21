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

    /** Upper bound for imported hashes: a huge count would block the device on every password check. */
    const val MAX_ITERATIONS = 600_000
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
        if (!isHash(stored)) return false
        val iterations = parts[1].toInt()
        return try {
            val decoder = Base64.getDecoder()
            val expected = decoder.decode(parts[3])
            MessageDigest.isEqual(expected, derive(password, decoder.decode(parts[2]), iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    /** Well-formed hash with an iteration count between 1 and [MAX_ITERATIONS]. */
    fun isHash(value: String?): Boolean {
        val parts = value?.split('$') ?: return false
        return parts.size == 4 && parts[0] == PREFIX && parts[1].toIntOrNull() in 1..MAX_ITERATIONS
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
