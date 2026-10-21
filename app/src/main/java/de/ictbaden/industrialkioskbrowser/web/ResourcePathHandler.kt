package de.ictbaden.industrialkioskbrowser.web

import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import de.ictbaden.industrialkioskbrowser.config.AppConfig

/** Serves configuration resources at `https://appassets.androidplatform.net/res/<name>`. */
class ResourcePathHandler(private val config: () -> AppConfig) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val resource = config().resources[path] ?: return null
        val mime = mimeType(path)
        val encoding = if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else null
        return WebResourceResponse(mime, encoding, resource.bytes.inputStream())
    }

    companion object {
        const val PATH = "/res/"
        const val BASE_URL = "https://${UrlPolicy.ASSET_HOST}/res/"

        private val TYPES = mapOf(
            "html" to "text/html", "htm" to "text/html", "css" to "text/css", "js" to "text/javascript",
            "json" to "application/json", "txt" to "text/plain", "xml" to "application/xml",
            "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
            "webp" to "image/webp", "svg" to "image/svg+xml", "ico" to "image/x-icon",
            "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf",
            "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "wav" to "audio/wav", "m4a" to "audio/mp4",
            "pdf" to "application/pdf",
        )

        fun mimeType(path: String): String =
            TYPES[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
    }
}
