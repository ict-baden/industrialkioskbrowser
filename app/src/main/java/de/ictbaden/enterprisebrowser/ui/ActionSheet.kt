package de.ictbaden.enterprisebrowser.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.text.Layout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.color.MaterialColors
import de.ictbaden.enterprisebrowser.R

/** Bottom sheet with a header (avatar, title, subtitle) and a grid of action tiles. */
class ActionSheet(private val context: Context) {

    data class Action(
        val label: String,
        @param:DrawableRes val icon: Int,
        val active: Boolean = false,
        val enabled: Boolean = true,
        val onClick: () -> Unit,
    )

    fun show(title: String, subtitle: String, accent: Int, actions: List<Action>): BottomSheetDialog {
        val dialog = BottomSheetDialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
            addView(BottomSheetDragHandleView(context), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(header(title, subtitle, accent))
            addView(grid(actions, accent) { dialog.dismiss() })
        }
        dialog.setContentView(content)
        dialog.show()
        return dialog
    }

    private fun header(title: String, subtitle: String, accent: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(24), 0, dp(24), dp(8))
        addView(TextView(context).apply {
            text = Brand.initial(title)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_avatar)
            backgroundTintList = ColorStateList.valueOf(accent)
            setTextColor(Brand.onColor(accent))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(TextView(context).apply {
                text = title
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        })
    }

    private fun grid(actions: List<Action>, accent: Int, dismiss: () -> Unit) = GridLayout(context).apply {
        columnCount = 4
        setPadding(dp(8), dp(12), dp(8), 0)
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        for (action in actions) {
            addView(tile(action, if (action.active) accent else surface, if (action.active) Brand.onColor(accent) else onSurface) {
                dismiss()
                action.onClick()
            }, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
    }

    private fun tile(action: Action, circle: Int, iconColor: Int, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(10), dp(4), dp(10))
        isEnabled = action.enabled
        alpha = if (action.enabled) 1f else 0.38f
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
        }
        setBackgroundResource(ripple.resourceId)
        if (action.enabled) setOnClickListener { onClick() }
        addView(ImageView(context).apply {
            setImageResource(action.icon)
            imageTintList = ColorStateList.valueOf(iconColor)
            setBackgroundResource(R.drawable.bg_tile_icon)
            backgroundTintList = ColorStateList.valueOf(circle)
            scaleType = ImageView.ScaleType.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(TextView(context).apply {
            text = action.label
            gravity = Gravity.CENTER
            maxLines = 2
            // Long single words ("Configuración", "Einstellungen") are hyphenated instead of cut mid-word.
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
