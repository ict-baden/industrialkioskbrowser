package de.ictbaden.pforte.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.ictbaden.pforte.R
import de.ictbaden.pforte.config.ConfigStore
import de.ictbaden.pforte.config.PasswordHasher

object Dialogs {

    fun requireAdmin(activity: Activity, onSuccess: () -> Unit) = requirePassword(
        activity,
        ConfigStore.get(activity).config.global.adminPasswordHash,
        activity.getString(R.string.admin_password),
        onSuccess,
    )

    /** Runs [onSuccess] directly when no password is set, otherwise after a correct entry. */
    fun requirePassword(activity: Activity, hash: String?, title: String, onSuccess: () -> Unit) {
        if (hash == null) {
            onSuccess()
            return
        }
        val field = textField(activity, activity.getString(R.string.password), password = true)
        MaterialAlertDialogBuilder(activity)
            .setIcon(icon(activity, R.drawable.ic_lock))
            .setTitle(title)
            .setView(form(activity, field))
            .setPositiveButton(R.string.button_unlock) { _, _ ->
                if (PasswordHasher.verify(field.value, hash)) {
                    onSuccess()
                } else {
                    Toast.makeText(activity, R.string.wrong_password, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        field.editText?.requestFocus()
    }

    /** Asks for HTTP credentials; [onResult] receives `null` when cancelled. */
    fun credentials(activity: Activity, host: String, onResult: (Pair<String, String>?) -> Unit) {
        val user = textField(activity, activity.getString(R.string.user_name))
        val password = textField(activity, activity.getString(R.string.password), password = true)
        var answered = false
        MaterialAlertDialogBuilder(activity)
            .setIcon(icon(activity, R.drawable.ic_key))
            .setTitle(R.string.sign_in)
            .setMessage(host)
            .setView(form(activity, user, password))
            .setPositiveButton(R.string.button_sign_in) { _, _ ->
                answered = true
                onResult(user.value to password.value)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .setOnDismissListener { if (!answered) onResult(null) }
            .show()
    }

    /** Icon tinted with the theme's primary color (the vector sources are white). */
    fun icon(context: Context, @DrawableRes res: Int): Drawable? =
        ContextCompat.getDrawable(context, res)?.mutate()?.apply {
            setTint(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, Color.BLACK))
        }

    /** Outlined Material text field. */
    fun textField(
        context: Context,
        hint: String,
        value: String = "",
        password: Boolean = false,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        helper: String? = null,
    ) = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
        this.hint = hint
        helper?.let { helperText = it }
        if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        addView(TextInputEditText(this.context).apply {
            setText(value)
            isSingleLine = true
            this.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else inputType
        })
    }

    /** Vertical form with dialog padding. */
    fun form(context: Context, vararg views: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val density = context.resources.displayMetrics.density
        setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
        views.forEach { view ->
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            })
        }
    }
}

/** Current text of a Material text field. */
val TextInputLayout.value: String get() = editText?.text?.toString().orEmpty()
