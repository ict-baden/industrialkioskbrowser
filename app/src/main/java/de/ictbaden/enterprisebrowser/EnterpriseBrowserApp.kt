package de.ictbaden.enterprisebrowser

import android.app.Application
import de.ictbaden.enterprisebrowser.config.ConfigStore
import de.ictbaden.enterprisebrowser.ui.AppLanguage

class EnterpriseBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the first activity, so it already starts in the configured language.
        AppLanguage.apply(ConfigStore.get(this).config.global.language)
    }
}
