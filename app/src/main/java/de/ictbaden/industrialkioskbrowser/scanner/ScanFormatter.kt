package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.Replacement
import de.ictbaden.industrialkioskbrowser.config.RuleAction
import de.ictbaden.industrialkioskbrowser.config.ScanDataSettings
import de.ictbaden.industrialkioskbrowser.config.ScanRule
import de.ictbaden.industrialkioskbrowser.config.TextCase

/**
 * Formats a barcode before it is entered (`scanner.data`, same rules as Industrial Keyboard).
 *
 * Order: group separator / trim → strip → remove spaces → replace → case → prefix and suffix.
 * Rules are checked on the text after the first step; the first matching rule decides.
 */
object ScanFormatter {

    const val GS = '\u001D'

    /**
     * The text to enter, or `null` when the code is dropped (by a rule, by `unmatched: drop`, or
     * because nothing is left of it). [symbology] is a canonical name or `null` when unknown.
     */
    fun format(text: String, symbology: String?, settings: ScanDataSettings): String? {
        var value = text
        settings.gsReplacement?.let { value = value.replace(GS.toString(), it) }
        // Java counts GS (U+001D) as whitespace; it carries GS1 data and stays.
        if (settings.trim) value = value.trim { it != GS && (it.isWhitespace() || it.isISOControl()) }

        val rule = settings.rules.firstOrNull { matches(it, value, symbology) }
        if (rule == null && settings.rules.isNotEmpty() && settings.unmatched == RuleAction.DROP) return null
        if (rule?.action == RuleAction.DROP) return null

        val stripLeading = rule?.stripLeading ?: settings.stripLeading
        val stripTrailing = rule?.stripTrailing ?: settings.stripTrailing
        value = if (stripLeading + stripTrailing >= value.length) "" else value.substring(stripLeading, value.length - stripTrailing)
        if (rule?.removeSpaces ?: settings.removeSpaces) value = value.replace(" ", "")
        value = replace(value, rule?.replace ?: settings.replace)
        value = when (rule?.case ?: settings.case) {
            TextCase.NONE -> value
            TextCase.UPPER -> value.uppercase()
            TextCase.LOWER -> value.lowercase()
        }
        if (value.isEmpty()) return null
        return (rule?.prefix ?: settings.prefix) + value + (rule?.suffix ?: settings.suffix).orEmpty()
    }

    /** Whether [rule] applies; an unknown symbology only matches rules without `symbologies`. */
    fun matches(rule: ScanRule, text: String, symbology: String?): Boolean {
        if (rule.symbologies.isNotEmpty() && (symbology == null || symbology !in rule.symbologies)) return false
        if (rule.minLength > 0 && text.length < rule.minLength) return false
        if (rule.maxLength > 0 && text.length > rule.maxLength) return false
        if (rule.startsWith != null && !text.startsWith(rule.startsWith)) return false
        if (rule.pattern != null && !Regex(rule.pattern).matches(text)) return false
        return true
    }

    private fun replace(text: String, replacements: List<Replacement>): String =
        replacements.fold(text) { value, r ->
            if (r.regex) Regex(r.find).replace(value, r.with) else value.replace(r.find, r.with)
        }
}
