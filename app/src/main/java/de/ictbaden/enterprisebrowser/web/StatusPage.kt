package de.ictbaden.enterprisebrowser.web

import android.content.Context

/**
 * Renders error pages from `assets/status.html` or a configured template. Placeholders
 * `{{title}}`, `{{message}}`, `{{url}}` and `{{color}}` are case-insensitive; the retry link uses [RETRY_URL].
 */
object StatusPage {
    const val RETRY_URL = "ebrowser://retry"
    const val BASE_URL = "https://${UrlPolicy.ASSET_HOST}/status"

    fun render(context: Context, template: String?, title: String, message: String, url: String?, color: String = "#0D47A1"): String =
        fill(template ?: context.assets.open("status.html").bufferedReader().use { it.readText() }, title, message, url, color)

    // Plain replace, no regex: Android's ICU regex rejects an unescaped "}" that the JVM accepts,
    // so every error page crashed on the device while the unit tests passed.
    fun fill(template: String, title: String, message: String, url: String?, color: String = "#0D47A1"): String = template
        .replace("{{color}}", escape(color), ignoreCase = true)
        .replace("{{title}}", escape(title), ignoreCase = true)
        .replace("{{message}}", escape(message), ignoreCase = true)
        .replace("{{url}}", escape(url.orEmpty()), ignoreCase = true)

    private fun escape(text: String) = buildString(text.length) {
        for (c in text) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }
}
