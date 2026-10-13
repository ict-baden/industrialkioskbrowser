package io.github.pieewiee.enterprisebrowser.web

import android.app.Activity
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.github.pieewiee.enterprisebrowser.config.HotkeyAction
import io.github.pieewiee.enterprisebrowser.config.KeyboardSettings
import io.github.pieewiee.enterprisebrowser.config.SoftKeyboard
import org.json.JSONObject

/**
 * Hardware key remapping, hotkeys, macros and soft-keyboard visibility for one browser session.
 *
 * The soft keyboard is kept away with `FLAG_ALT_FOCUSABLE_IM` (the window then never talks to
 * an IME, hardware keys still reach the page) and released on demand.
 */
class KeyboardController(
    private val activity: Activity,
    private val webView: WebView,
    private val settings: KeyboardSettings,
    private val perform: (BrowserAction) -> Unit,
) {
    private val remaps: Map<Int, String> = settings.remap.mapNotNull { remap ->
        Macro.keyCodeOf(remap.key)?.let { it to remap.send } ?: null.also { Log.w(TAG, "Unknown key \"${remap.key}\"") }
    }.toMap()

    private val hotkeys: Map<Int, BrowserAction> = settings.hotkeys.mapNotNull { (action, key) ->
        val code = Macro.keyCodeOf(key) ?: return@mapNotNull null.also { Log.w(TAG, "Unknown hotkey \"$key\"") }
        code to when (HotkeyAction.entries.first { it.key == action }) {
            HotkeyAction.TOGGLE_KEYBOARD -> BrowserAction.TOGGLE_KEYBOARD
            HotkeyAction.MENU -> BrowserAction.MENU
            HotkeyAction.HOME -> BrowserAction.HOME
            HotkeyAction.RELOAD -> BrowserAction.RELOAD
            HotkeyAction.BACK -> BrowserAction.BACK
            HotkeyAction.FORWARD -> BrowserAction.FORWARD
            HotkeyAction.SCAN -> BrowserAction.SCAN
        }
    }.toMap()

    private var heldDigit: Int? = null
    private var digitLongPressed = false

    val softKeyboardAvailable get() = settings.soft != SoftKeyboard.NONE

    private val imeVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(webView)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    /** Called when a page starts loading. */
    fun onPageStarted() {
        if (settings.soft == SoftKeyboard.NONE || !settings.showOnFocus) suppressIme(true)
    }

    fun onPageFinished() {
        if (settings.soft == SoftKeyboard.SYSTEM && settings.showOnPageLoad) showIme()
    }

    fun toggleSoftKeyboard() {
        if (!softKeyboardAvailable) return
        if (imeVisible) {
            WindowCompat.getInsetsController(activity.window, webView).hide(WindowInsetsCompat.Type.ime())
            if (!settings.showOnFocus) suppressIme(true)
        } else {
            showIme()
        }
    }

    /** Handles the hardware back key; returns false when the default back behaviour should run. */
    fun onBack(): Boolean {
        val macro = settings.backKey.trim()
        if (macro.equals("{BACK}", ignoreCase = true)) return false
        runMacro(macro)
        return true
    }

    /** Returns true when [event] was consumed. */
    fun dispatch(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) return false
        hotkeys[code]?.let { action ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) perform(action)
            return true
        }
        remaps[code]?.let { send ->
            if (send.equals("system", ignoreCase = true)) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) runMacro(send)
            return true
        }
        if (settings.longPressDigitsAsF && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && event.metaState == 0) {
            return longPressDigit(event)
        }
        return false
    }

    /** Long press on 1–9/0 sends F1–F10; a short press types the digit. */
    private fun longPressDigit(event: KeyEvent): Boolean {
        val code = event.keyCode
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                heldDigit = code
                digitLongPressed = false
            } else if (!digitLongPressed && heldDigit == code) {
                digitLongPressed = true
                val n = if (code == KeyEvent.KEYCODE_0) 10 else code - KeyEvent.KEYCODE_0
                pressKey(KeyEvent.KEYCODE_F1 + n - 1)
            }
            KeyEvent.ACTION_UP -> {
                if (heldDigit == code && !digitLongPressed) pressKey(code)
                heldDigit = null
            }
        }
        return true
    }

    fun runMacro(macro: String) {
        var delay = 0L
        for (step in Macro.parse(macro)) {
            if (step is MacroStep.Pause) {
                delay += step.millis
                continue
            }
            webView.postDelayed({ run(step) }, delay)
            delay += STEP_DELAY_MS
        }
    }

    fun pressKey(keyCode: Int, metaState: Int = 0) {
        webView.requestFocus()
        val now = android.os.SystemClock.uptimeMillis()
        webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        webView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun run(step: MacroStep) {
        when (step) {
            is MacroStep.Key -> pressKey(step.keyCode, step.metaState)
            is MacroStep.Text -> typeText(step.text)
            is MacroStep.Action -> perform(step.action)
            is MacroStep.Pause -> Unit
        }
    }

    private fun typeText(text: String) {
        webView.requestFocus()
        val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        if (events != null) {
            events.forEach(webView::dispatchKeyEvent)
        } else {
            // Characters without a key mapping (e.g. umlauts) are inserted directly.
            webView.evaluateJavascript("document.execCommand('insertText', false, ${JSONObject.quote(text)})", null)
        }
    }

    private fun showIme() {
        suppressIme(false)
        webView.requestFocus()
        WindowCompat.getInsetsController(activity.window, webView).show(WindowInsetsCompat.Type.ime())
    }

    private fun suppressIme(suppress: Boolean) {
        if (suppress) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            WindowCompat.getInsetsController(activity.window, webView).hide(WindowInsetsCompat.Type.ime())
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    companion object {
        private const val TAG = "KeyboardController"
        private const val STEP_DELAY_MS = 50L
    }
}
