package de.ictbaden.industrialkioskbrowser.admin

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.ui.Brand

/**
 * Building blocks of the settings screens (sections, cards with rows, switches) in the style of
 * the admin screen; shared by [AdminActivity] and [ProfileSettingsActivity].
 */
class SettingsUi(private val context: Context, private val content: LinearLayout) {

    /** Brand color of the current render; tints the icons. */
    var brandColor = 0

    fun section(title: String) = content.addView(TextView(context).apply {
        text = title
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        setTextColor(accent())
        setPadding(dp(8), dp(24), dp(8), dp(8))
    })

    fun card(rows: List<View>) {
        val card = MaterialCardView(context).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                column.addView(MaterialDivider(context).apply {
                    dividerInsetStart = dp(72)
                    dividerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                })
            }
            column.addView(row)
        }
        card.addView(column)
        content.addView(card)
    }

    fun row(
        @DrawableRes icon: Int,
        title: String,
        subtitle: String?,
        trailing: View? = null,
        onClick: (() -> Unit)?,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        if (onClick != null) {
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        addView(iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(texts(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16)
        })
        trailing?.let { addView(it) }
    }

    fun switchRow(@DrawableRes icon: Int, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val toggle = MaterialSwitch(context).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        return row(icon, title, subtitle, trailing = toggle) { toggle.toggle() }
    }

    fun texts(title: String, subtitle: String?) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        subtitle?.let {
            addView(TextView(context).apply {
                text = it
                tag = SUBTITLE_TAG
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
    }

    fun iconCircle(@DrawableRes icon: Int) = ImageView(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.CENTER
        imageTintList = ColorStateList.valueOf(accent())
        setBackgroundResource(R.drawable.bg_tile_icon)
        backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(brandColor, 0x1F))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Readable accent on light surfaces, even for very light brand colors. */
    fun accent(): Int = if (Brand.isLight(brandColor)) Brand.darker(brandColor, 0.45f) else brandColor

    fun ripple(borderless: Boolean = false): Int = TypedValue().also {
        context.theme.resolveAttribute(
            if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground,
            it,
            true,
        )
    }.resourceId

    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        const val SUBTITLE_TAG = "subtitle"
    }
}
