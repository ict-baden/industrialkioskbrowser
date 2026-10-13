package io.github.pieewiee.enterprisebrowser.web

import android.content.Context

/**
 * Renders error pages from `assets/status.html` or a configured template. Placeholders
 * `{{title}}`, `{{message}}` and `{{url}}` are case-insensitive; the retry link uses [RETRY_URL].
 */
object StatusPage {
    const val RETRY_URL = "ebrowser://retry"
    const val BASE_URL = "https://${UrlPolicy.ASSET_HOST}/status"

    fun render(context: Context, template: String?, title: String, message: String, url: String?): String =
        fill(template ?: context.assets.open("status.html").bufferedReader().use { it.readText() }, title, message, url)

    fun fill(template: String, title: String, message: String, url: String?): String = template
        .replace(Regex("\\{\\{title}}", RegexOption.IGNORE_CASE), Regex.escapeReplacement(escape(title)))
        .replace(Regex("\\{\\{message}}", RegexOption.IGNORE_CASE), Regex.escapeReplacement(escape(message)))
        .replace(Regex("\\{\\{url}}", RegexOption.IGNORE_CASE), Regex.escapeReplacement(escape(url.orEmpty())))

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
