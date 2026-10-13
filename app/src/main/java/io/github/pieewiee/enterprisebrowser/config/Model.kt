package io.github.pieewiee.enterprisebrowser.config

/** Keys sent after a scan was inserted into the focused field, emulating a keyboard wedge. */
enum class ScanTerminator(val key: String) {
    NONE("none"),
    TAB("tab"),
    ENTER("enter"),
    TAB_ENTER("tab+enter"),
    ENTER_TAB("enter+tab");

    companion object {
        fun parse(value: String?, fallback: ScanTerminator = ENTER): ScanTerminator =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: fallback
    }
}

enum class ScreenOrientation(val key: String) {
    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun parse(value: String?): ScreenOrientation =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: AUTO
    }
}

data class ScannerSettings(
    val enabled: Boolean = true,
    val terminator: ScanTerminator = ScanTerminator.ENTER,
    val injectIntoFocusedField: Boolean = true,
)

data class KioskSettings(
    val hideToolbar: Boolean = false,
    val immersive: Boolean = false,
    val keepScreenOn: Boolean = true,
    val orientation: ScreenOrientation = ScreenOrientation.AUTO,
)

data class Profile(
    val id: String,
    val name: String,
    val startUrl: String,
    /** Hosts (and their subdomains) the browser may navigate to. Empty means no restriction. */
    val allowList: List<String> = emptyList(),
    val launchOnStartup: Boolean = false,
    val userAgent: String? = null,
    val textZoom: Int = 100,
    val clearCacheOnStart: Boolean = false,
    val clearCookiesOnStart: Boolean = false,
    val allowFileUpload: Boolean = false,
    val allowDownloads: Boolean = true,
    val pinchZoom: Boolean = true,
    val kiosk: KioskSettings = KioskSettings(),
    val scanner: ScannerSettings = ScannerSettings(),
)

data class GlobalSettings(
    val adminPasswordHash: String? = null,
    val exitPasswordHash: String? = null,
    val hideExit: Boolean = false,
)

data class AppConfig(
    val global: GlobalSettings = GlobalSettings(),
    val profiles: List<Profile> = emptyList(),
) {
    fun profile(id: String?): Profile? = profiles.firstOrNull { it.id == id }

    fun upsert(profile: Profile): AppConfig {
        val index = profiles.indexOfFirst { it.id == profile.id }
        val updated = if (index < 0) profiles + profile else profiles.toMutableList().also { it[index] = profile }
        return copy(profiles = updated)
    }

    fun remove(id: String): AppConfig = copy(profiles = profiles.filterNot { it.id == id })

    companion object {
        const val DEMO_PROFILE_ID = "demo"
        const val DEMO_URL = "https://appassets.androidplatform.net/assets/demo/index.html"

        fun initial(): AppConfig = AppConfig(
            profiles = listOf(
                Profile(id = DEMO_PROFILE_ID, name = "Demo & Scanner-Test", startUrl = DEMO_URL),
            ),
        )
    }
}
