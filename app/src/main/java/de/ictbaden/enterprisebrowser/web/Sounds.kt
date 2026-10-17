package de.ictbaden.enterprisebrowser.web

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import de.ictbaden.enterprisebrowser.config.AppConfig
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays sound specs: `none`, `beep`, `error`, `tone:<hz>:<ms>[:<hz>:<ms>…]` or `res:<name>`
 * (an audio file from the configuration's resources).
 */
class Sounds(private val context: Context, private val config: AppConfig) {

    fun play(spec: String) {
        val value = spec.trim()
        when {
            value.isEmpty() || value == "none" -> Unit
            value == "beep" -> tones(listOf(1000 to 100))
            value == "error" -> tones(listOf(1400 to 200, 1000 to 200))
            value.startsWith("tone:") -> parseTones(value.removePrefix("tone:"))?.let(::tones)
                ?: Log.w(TAG, "Invalid tone spec \"$value\"")
            value.startsWith(AppConfig.RES_PREFIX) -> playResource(value.removePrefix(AppConfig.RES_PREFIX))
            else -> Log.w(TAG, "Unknown sound \"$value\"")
        }
    }

    private fun tones(tones: List<Pair<Int, Int>>) = thread(name = "tone") {
        val samples = tones.flatMap { (hz, ms) -> sine(hz, ms).asIterable() + ShortArray(SAMPLE_RATE * 30 / 1000).asIterable() }
            .toShortArray()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        try {
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(samples.size * 1000L / SAMPLE_RATE + 50)
        } catch (e: Exception) {
            Log.w(TAG, "Tone playback failed", e)
        } finally {
            track.release()
        }
    }

    private fun playResource(name: String) {
        val resource = config.resources[name] ?: run {
            Log.w(TAG, "Unknown sound resource \"$name\"")
            return
        }
        val file = File(context.cacheDir, "sound-${name.hashCode()}-${resource.bytes.size}")
        if (!file.exists()) file.writeBytes(resource.bytes)
        MediaPlayer().apply {
            setDataSource(file.path)
            setOnCompletionListener { it.release() }
            setOnErrorListener { player, _, _ -> player.release(); true }
            prepare()
            start()
        }
    }

    private fun sine(hz: Int, ms: Int): ShortArray {
        val count = SAMPLE_RATE * ms / 1000
        return ShortArray(count) { i -> (sin(2 * PI * i * hz / SAMPLE_RATE) * Short.MAX_VALUE * 0.6).toInt().toShort() }
    }

    companion object {
        private const val TAG = "Sounds"
        private const val SAMPLE_RATE = 22_050

        fun parseTones(spec: String): List<Pair<Int, Int>>? {
            val numbers = spec.split(':').map { it.trim().toIntOrNull() ?: return null }
            if (numbers.isEmpty() || numbers.size % 2 != 0) return null
            return numbers.chunked(2).map { (hz, ms) -> hz.coerceIn(100, 8000) to ms.coerceIn(10, 2000) }
        }
    }
}
