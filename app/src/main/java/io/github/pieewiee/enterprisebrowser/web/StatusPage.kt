package io.github.pieewiee.enterprisebrowser.web

import android.content.Context

/** Renders `assets/status.html` for load errors. Its retry link uses [RETRY_URL]. */
object StatusPage {
    const val RETRY_URL = "ebrowser://retry"
    const val BASE_URL = "https://${UrlPolicy.ASSET_HOST}/status"

    fun render(context: Context, title: String, message: String, url: String?): String {
        val template = context.assets.open("status.html").bufferedReader().use { it.readText() }
        return template
            .replace("{{TITLE}}", escape(title))
            .replace("{{MESSAGE}}", escape(message))
            .replace("{{URL}}", escape(url.orEmpty()))
    }

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
