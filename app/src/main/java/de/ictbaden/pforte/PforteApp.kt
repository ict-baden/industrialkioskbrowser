package de.ictbaden.pforte

import android.app.Application
import de.ictbaden.pforte.config.ConfigStore
import de.ictbaden.pforte.ui.AppLanguage

class PforteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the first activity, so it already starts in the configured language.
        AppLanguage.apply(ConfigStore.get(this).config.global.language)
    }
}
