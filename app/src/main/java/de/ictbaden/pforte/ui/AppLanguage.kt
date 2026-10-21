package de.ictbaden.pforte.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import de.ictbaden.pforte.config.Languages

/**
 * Applies `global.language` (richtlinien/13-sprachen.md): `auto` follows the device, otherwise the
 * configured language. The configuration is the source of truth, so it is applied on every start.
 */
object AppLanguage {

    /** Changes the app language; open activities are recreated only when it actually changes. */
    fun apply(language: String) {
        val wanted = if (language == Languages.AUTO) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }

    /** Language names in their own language, so everyone finds theirs. */
    val NAMES = mapOf("en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "zh-Hans" to "简体中文")
}
