package de.ictbaden.enterprisebrowser.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import de.ictbaden.enterprisebrowser.R
import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.Profile
import de.ictbaden.enterprisebrowser.web.UrlPolicy

/** Brand colors and logo from the configuration (`global.branding`, `profile.color`). */
object Brand {

    fun color(context: Context, config: AppConfig, profile: Profile? = null): Int =
        parse(profile?.color) ?: parse(config.global.branding.color) ?: ContextCompat.getColor(context, R.color.brand)

    fun parse(hex: String?): Int? = hex?.let { runCatching { it.toColorInt() }.getOrNull() }

    fun isLight(color: Int) = ColorUtils.calculateLuminance(color) > 0.6

    /** Text and icon color for content drawn on [color]. */
    fun onColor(color: Int): Int = if (isLight(color)) 0xDE000000.toInt() else Color.WHITE

    fun onColorSecondary(color: Int): Int = if (isLight(color)) 0x99000000.toInt() else 0xCCFFFFFF.toInt()

    fun darker(color: Int, amount: Float = 0.3f): Int = ColorUtils.blendARGB(color, Color.BLACK, amount)

    /** Diagonal gradient with rounded bottom corners for the start screen header. */
    fun headerBackground(color: Int, cornerRadius: Float) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.12f), darker(color, 0.35f)),
    ).apply {
        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
    }

    /** Light or dark status bar icons depending on the color behind them. */
    fun applySystemBars(activity: Activity, colorBehindStatusBar: Int) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = isLight(colorBehindStatusBar)
        }
    }

    fun logo(config: AppConfig): Bitmap? {
        val name = config.global.branding.logo?.removePrefix(AppConfig.RES_PREFIX) ?: return null
        val bytes = config.resources[name]?.bytes ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** First letter for avatar circles. */
    fun initial(name: String): String = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

    /** Host part of a URL for subtitles. */
    fun host(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore('?')

    /** Address shown to users: the host, or a label for the bundled demo. */
    fun address(url: String): String = host(url).let { if (it == UrlPolicy.ASSET_HOST) "Built-in demo" else it }
}
