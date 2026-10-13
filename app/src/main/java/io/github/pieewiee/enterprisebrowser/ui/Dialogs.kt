package io.github.pieewiee.enterprisebrowser.ui

import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.PasswordHasher

object Dialogs {

    fun requireAdmin(activity: Activity, onSuccess: () -> Unit) = requirePassword(
        activity,
        ConfigStore.get(activity).config.global.adminPasswordHash,
        "Admin-Passwort",
        onSuccess,
    )

    /** Runs [onSuccess] directly when no password is set, otherwise after a correct entry. */
    fun requirePassword(activity: Activity, hash: String?, title: String, onSuccess: () -> Unit) {
        if (hash == null) {
            onSuccess()
            return
        }
        val input = passwordField(activity, "Passwort")
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(padded(input))
            .setPositiveButton("OK") { _, _ ->
                if (PasswordHasher.verify(input.text.toString(), hash)) {
                    onSuccess()
                } else {
                    Toast.makeText(activity, "Falsches Passwort", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
        input.requestFocus()
    }

    /** Asks for HTTP credentials; [onResult] receives `null` when cancelled. */
    fun credentials(activity: Activity, host: String, onResult: (Pair<String, String>?) -> Unit) {
        val user = EditText(activity).apply { hint = "Benutzername"; isSingleLine = true }
        val password = passwordField(activity, "Passwort")
        val fields = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(user)
            addView(password)
        }
        AlertDialog.Builder(activity)
            .setTitle("Anmeldung für $host")
            .setView(padded(fields))
            .setPositiveButton("Anmelden") { _, _ -> onResult(user.text.toString() to password.text.toString()) }
            .setNegativeButton("Abbrechen") { _, _ -> onResult(null) }
            .setOnCancelListener { onResult(null) }
            .show()
    }

    fun passwordField(activity: Activity, hint: String) = EditText(activity).apply {
        this.hint = hint
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    fun padded(view: View): View = FrameLayout(view.context).apply {
        val padding = (20 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, 0)
        addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

/** Pads [view] so content stays clear of system bars, cutouts and the soft keyboard (edge-to-edge). */
fun applySystemInsets(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime(),
        )
        v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
