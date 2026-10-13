package io.github.pieewiee.enterprisebrowser.web

import android.view.KeyEvent

/**
 * Key macros in `{TOKEN}` syntax, e.g. `{F1}`, `abc{TAB}`, `{CTRL-C}`,
 * `{PAUSE:200}`, `{NAVIGATE-HOME}`. Tokens are case-insensitive; unknown tokens are typed literally.
 */
sealed interface MacroStep {
    data class Text(val text: String) : MacroStep
    data class Key(val keyCode: Int, val metaState: Int = 0) : MacroStep
    data class Pause(val millis: Long) : MacroStep
    data class Action(val action: BrowserAction) : MacroStep
}

enum class BrowserAction { BACK, FORWARD, STOP, HOME, RELOAD, SCAN, MENU, TOGGLE_KEYBOARD }

object Macro {

    private val KEYS: Map<String, Int> = buildMap {
        for (n in 1..12) put("F$n", KeyEvent.KEYCODE_F1 + n - 1)
        put("TAB", KeyEvent.KEYCODE_TAB)
        put("ENTER", KeyEvent.KEYCODE_ENTER)
        put("RETURN", KeyEvent.KEYCODE_ENTER)
        put("CR", KeyEvent.KEYCODE_ENTER)
        put("ESC", KeyEvent.KEYCODE_ESCAPE)
        put("ESCAPE", KeyEvent.KEYCODE_ESCAPE)
        put("BS", KeyEvent.KEYCODE_DEL)
        put("BACKSPACE", KeyEvent.KEYCODE_DEL)
        put("DELETE", KeyEvent.KEYCODE_FORWARD_DEL)
        put("DEL", KeyEvent.KEYCODE_FORWARD_DEL)
        put("INSERT", KeyEvent.KEYCODE_INSERT)
        put("HOME", KeyEvent.KEYCODE_MOVE_HOME)
        put("END", KeyEvent.KEYCODE_MOVE_END)
        put("PAGE-UP", KeyEvent.KEYCODE_PAGE_UP)
        put("ROLL-UP", KeyEvent.KEYCODE_PAGE_UP)
        put("PAGE-DOWN", KeyEvent.KEYCODE_PAGE_DOWN)
        put("ROLL-DOWN", KeyEvent.KEYCODE_PAGE_DOWN)
        put("UP", KeyEvent.KEYCODE_DPAD_UP)
        put("DOWN", KeyEvent.KEYCODE_DPAD_DOWN)
        put("LEFT", KeyEvent.KEYCODE_DPAD_LEFT)
        put("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT)
        put("SPACE", KeyEvent.KEYCODE_SPACE)
    }

    private val ACTIONS: Map<String, BrowserAction> = mapOf(
        "BACK" to BrowserAction.BACK,
        "NAVIGATE-BACK" to BrowserAction.BACK,
        "NAVIGATE-FORWARD" to BrowserAction.FORWARD,
        "NAVIGATE-STOP" to BrowserAction.STOP,
        "NAVIGATE-HOME" to BrowserAction.HOME,
        "NAVIGATE-REFRESH" to BrowserAction.RELOAD,
        "SPECIAL:SCAN" to BrowserAction.SCAN,
        "MENU" to BrowserAction.MENU,
        "KEYBOARD" to BrowserAction.TOGGLE_KEYBOARD,
    )

    private val TEXT_TOKENS = mapOf("OPEN-BRACE" to "{", "CLOSE-BRACE" to "}")

    fun parse(macro: String): List<MacroStep> {
        val steps = mutableListOf<MacroStep>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) steps += MacroStep.Text(text.toString()).also { text.clear() }
        }
        var i = 0
        while (i < macro.length) {
            val close = if (macro[i] == '{') macro.indexOf('}', i + 1) else -1
            if (close < 0) {
                text.append(macro[i++])
                continue
            }
            val raw = macro.substring(i + 1, close)
            val step = token(raw.trim().uppercase())
            if (step == null) {
                text.append(macro, i, close + 1)
            } else if (step is MacroStep.Text) {
                text.append(step.text)
            } else {
                flush()
                steps += step
            }
            i = close + 1
        }
        flush()
        return steps
    }

    private fun token(token: String): MacroStep? {
        KEYS[token]?.let { return MacroStep.Key(it) }
        ACTIONS[token]?.let { return MacroStep.Action(it) }
        TEXT_TOKENS[token]?.let { return MacroStep.Text(it) }
        if (token == "BACK-TAB") return MacroStep.Key(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
        if (token == "PAUSE") return MacroStep.Pause(250)
        if (token.startsWith("PAUSE:")) return token.substringAfter(':').toLongOrNull()?.coerceIn(0, 10_000)?.let(MacroStep::Pause)
        if (token.startsWith("HEX:")) {
            return token.substringAfter(':').toIntOrNull(16)
                ?.takeIf { it in 0x20..0xFFFF && it !in 0xE000..0xF8FF }
                ?.let { MacroStep.Text(it.toChar().toString()) }
        }
        val ctrl = Regex("^CTRL[-+]([A-Z])$").find(token)
        if (ctrl != null) {
            val letter = ctrl.groupValues[1][0]
            return MacroStep.Key(KeyEvent.KEYCODE_A + (letter - 'A'), KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
        }
        return null
    }

    /** Resolves a key name (`F1`, `BUTTON_L1`, `KEYCODE_TAB`) or numeric code to an Android key code. */
    fun keyCodeOf(name: String): Int? {
        val trimmed = name.trim()
        trimmed.toIntOrNull()?.let { return it.takeIf { code -> code > 0 } }
        val upper = trimmed.uppercase()
        val code = KeyEvent.keyCodeFromString(if (upper.startsWith("KEYCODE_")) upper else "KEYCODE_$upper")
        return code.takeIf { it != KeyEvent.KEYCODE_UNKNOWN }
    }
}
