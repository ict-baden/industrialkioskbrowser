package de.ictbaden.enterprisebrowser.web

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import de.ictbaden.enterprisebrowser.BuildConfig
import org.json.JSONObject

/**
 * Native end of `window.EnterpriseBrowser` (see `assets/js/bridge.js`). Messages are JSON
 * `{id, cmd, args}`; every message is answered with `{id, result}` or `{id, error}`.
 * WebView only delivers messages from the origins the listener was registered for.
 */
// Only registered after WebViewFeature.WEB_MESSAGE_LISTENER was checked, so replies are supported.
@SuppressLint("RequiresFeature")
class BridgeHandler(private val context: Context, private val host: Host) : WebViewCompat.WebMessageListener {

    interface Host {
        val profileId: String
        val scannerName: String
        val scannerEnabled: Boolean
        fun setScannerEnabledByPage(enabled: Boolean)
        fun softTrigger(start: Boolean?)
    }

    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val request = runCatching { JSONObject(message.data ?: "") }.getOrNull() ?: return
        val id = request.opt("id")
        val args = request.optJSONObject("args") ?: JSONObject()
        val reply = JSONObject().put("id", id)
        try {
            reply.put("result", handle(request.optString("cmd"), args) ?: JSONObject.NULL)
        } catch (e: Exception) {
            Log.w(TAG, "Bridge call from $sourceOrigin failed", e)
            reply.put("error", e.message ?: e.javaClass.simpleName)
        }
        replyProxy.postMessage(reply.toString())
    }

    private fun handle(cmd: String, args: JSONObject): Any? = when (cmd) {
        "beep" -> {
            val tone = if (args.optString("kind") == "error") ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_BEEP
            tones?.startTone(tone, 150)
            null
        }
        "vibrate" -> {
            vibrate(args.optLong("ms", 200).coerceIn(1, 5_000))
            null
        }
        "toast" -> {
            Toast.makeText(context, args.optString("text").take(500), Toast.LENGTH_SHORT).show()
            null
        }
        "deviceInfo" -> JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("profileId", host.profileId)
            put("scanner", host.scannerName)
        }
        "scanner.enable" -> host.setScannerEnabledByPage(true).let { null }
        "scanner.disable" -> host.setScannerEnabledByPage(false).let { null }
        "scanner.trigger" -> host.softTrigger(if (args.isNull("start")) null else args.optBoolean("start")).let { null }
        "scanner.status" -> JSONObject().put("name", host.scannerName).put("enabled", host.scannerEnabled)
        else -> throw IllegalArgumentException("Unknown command: $cmd")
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() {
        tones?.release()
    }

    companion object {
        const val JS_OBJECT_NAME = "EnterpriseBrowserNative"
        private const val TAG = "BridgeHandler"
    }
}
