package de.ictbaden.industrialkioskbrowser

import android.app.Application
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.scanner.ScanEngineDetector
import de.ictbaden.industrialkioskbrowser.ui.AppLanguage

class IndustrialKioskBrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before the first activity, so it already starts in the configured language.
        AppLanguage.apply(ConfigStore.get(this).config.global.language)
        // Asks DataWedge for its scanners early, so the first screen already knows whether to use the camera.
        ScanEngineDetector.start(this)
    }
}
