package de.ictbaden.industrialkioskbrowser

import android.app.Application
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.ui.AppLanguage

class IndustrialKioskBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the first activity, so it already starts in the configured language.
        AppLanguage.apply(ConfigStore.get(this).config.global.language)
    }
}
