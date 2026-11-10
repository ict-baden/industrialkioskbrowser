package de.ictbaden.industrialkioskbrowser.config

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Packs a configuration for a barcode and unpacks it again (same format as Industrial Keyboard):
 * minified JSON when it is short, otherwise `ICTZ1:` + base64url (no padding) of raw DEFLATE.
 */
object ConfigCodec {
    const val PREFIX = "ICTZ1:"

    /** Minified JSON up to this length goes into the barcode as it is (readable by any scanner). */
    const val PLAIN_LIMIT = 1800

    /** Limit for the unpacked text, against "zip bombs". */
    const val MAX_UNPACKED = 1024 * 1024

    /** Barcode payload for a JSON configuration text. */
    fun pack(json: String): String {
        val minified = try {
            JSONObject(json).toString()
        } catch (_: JSONException) {
            json.trim()
        }
        if (minified.length <= PLAIN_LIMIT) return minified
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(minified.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Plain JSON stays as it is; an `ICTZ1:` payload is unpacked. */
    fun unpack(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX)) return text
        val bytes = try {
            Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX).filterNot(Char::isWhitespace))
        } catch (_: IllegalArgumentException) {
            throw ConfigException("Invalid $PREFIX barcode: not base64url")
        }
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw ConfigException("Invalid $PREFIX barcode: data is cut off")
                }
                out.write(buffer, 0, n)
                if (out.size() > MAX_UNPACKED) throw ConfigException("Invalid $PREFIX barcode: more than 1 MB unpacked")
            }
        } catch (_: DataFormatException) {
            throw ConfigException("Invalid $PREFIX barcode: not DEFLATE data")
        } finally {
            inflater.end()
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
