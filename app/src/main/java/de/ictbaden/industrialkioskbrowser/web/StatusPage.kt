package de.ictbaden.industrialkioskbrowser.web

import android.content.Context
import de.ictbaden.industrialkioskbrowser.R

/**
 * Renders error pages from `assets/status.html` or a configured template. Placeholders
 * `{{title}}`, `{{message}}`, `{{url}}`, `{{color}}`, `{{code}}` (translated "Error code: IKB-…"),
 * `{{retry}}` (button label) and `{{lang}}` are case-insensitive; the retry link uses [RETRY_URL].
 */
object StatusPage {
    const val RETRY_URL = "industrialkioskbrowser://retry"

    /** Retry links of error page templates written for the former names (Pforte, EnterpriseBrowser); still accepted. */
    val LEGACY_RETRY_URLS = setOf("pforte://retry", "ebrowser://retry")
    const val BASE_URL = "https://${UrlPolicy.ASSET_HOST}/status"

    fun render(context: Context, template: String?, title: String, message: String, url: String?, color: String, code: String): String =
        fill(
            template ?: context.assets.open("status.html").bufferedReader().use { it.readText() },
            title, message, url, color,
            code = context.getString(R.string.error_code, code),
            retry = context.getString(R.string.error_retry),
            lang = context.resources.configuration.locales[0].toLanguageTag(),
        )

    // Plain replace, no regex: Android's ICU regex rejects an unescaped "}" that the JVM accepts,
    // so every error page crashed on the device while the unit tests passed.
    fun fill(
        template: String,
        title: String,
        message: String,
        url: String?,
        color: String = "#0D47A1",
        code: String = "",
        retry: String = "Try again",
        lang: String = "en",
    ): String = template
        .replace("{{color}}", escape(color), ignoreCase = true)
        .replace("{{title}}", escape(title), ignoreCase = true)
        .replace("{{message}}", escape(message), ignoreCase = true)
        .replace("{{url}}", escape(url.orEmpty()), ignoreCase = true)
        .replace("{{code}}", escape(code), ignoreCase = true)
        .replace("{{retry}}", escape(retry), ignoreCase = true)
        .replace("{{lang}}", escape(lang), ignoreCase = true)

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
