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

/** Shared parser for enums serialized by their `key`. */
inline fun <reified T> parseKey(value: String?, fallback: T): T where T : Enum<T>, T : Keyed =
    enumValues<T>().firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: fallback

interface Keyed {
    val key: String
}

enum class ScreenOrientation(override val key: String) : Keyed {
    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    companion object {
        fun parse(value: String?): ScreenOrientation = parseKey(value, AUTO)
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

enum class ViewportMode(override val key: String) : Keyed {
    /** The page's own `<meta name="viewport">` decides, like Chrome. */
    PAGE("page"),

    /** Always lay out at device width, ignoring the page's viewport width. */
    DEVICE("device"),

    /** Lay out at [ViewportSettings.width] CSS pixels and scale to the screen. */
    FIXED("fixed"),
}

data class ViewportSettings(
    val mode: ViewportMode = ViewportMode.PAGE,
    val width: Int = 0,
    /** Initial scale in percent; 0 lets WebView decide. */
    val initialScale: Int = 0,
    val fitToScreen: Boolean = true,
    val textZoom: Int = 100,
    val pinchZoom: Boolean = true,
    /** Replaces the content of the page's viewport meta tag. */
    val metaOverride: String? = null,
)

enum class SoftKeyboard(override val key: String) : Keyed {
    SYSTEM("system"),
    NONE("none"),
}

data class KeyRemap(
    /** Android key name without `KEYCODE_` (e.g. `F1`, `BUTTON_L1`) or a numeric key code. */
    val key: String,
    /** Macro to send, `system` to pass the key through, or empty to swallow it. */
    val send: String,
)

data class KeyboardSettings(
    val soft: SoftKeyboard = SoftKeyboard.SYSTEM,
    /** Show the soft keyboard when an input gets focus; when false it only opens via button or hotkey. */
    val showOnFocus: Boolean = true,
    val showOnPageLoad: Boolean = false,
    /** Macro for the hardware back key; `{BACK}` navigates back or opens the menu. */
    val backKey: String = "{BACK}",
    val remap: List<KeyRemap> = emptyList(),
    /** Action name (see [HotkeyAction]) to key name. */
    val hotkeys: Map<String, String> = emptyMap(),
    val longPressDigitsAsF: Boolean = false,
)

enum class HotkeyAction(override val key: String) : Keyed {
    TOGGLE_KEYBOARD("toggleKeyboard"),
    MENU("menu"),
    HOME("home"),
    RELOAD("reload"),
    BACK("back"),
    FORWARD("forward"),
    SCAN("scan"),
}

enum class MatchType(override val key: String) : Keyed {
    ALL("all"),
    CONTAINS("contains"),
    PREFIX("prefix"),
    REGEX("regex"),
}

enum class InjectionTiming(override val key: String) : Keyed {
    DOCUMENT_START("documentStart"),
    PAGE_LOADED("pageLoaded"),
}

enum class HtmlPosition(override val key: String) : Keyed {
    BODY_END("bodyEnd"),
    BODY_START("bodyStart"),
    HEAD("head"),
}

data class Injection(
    val urlMatch: String = "",
    val matchType: MatchType = MatchType.ALL,
    /** Only inject when the page's HTML contains this text (checked once the DOM is parsed). */
    val sourceContains: String? = null,
    val timing: InjectionTiming = InjectionTiming.DOCUMENT_START,
    val allFrames: Boolean = false,
    /** Inline content or `res:<name>`. */
    val css: String? = null,
    val js: String? = null,
    val html: String? = null,
    val htmlPosition: HtmlPosition = HtmlPosition.BODY_END,
)

data class ErrorPageSettings(
    /** HTML template (inline or `res:<name>`) with `{{title}}`, `{{message}}`, `{{url}}` placeholders. */
    val template: String? = null,
    /** Also replace server error pages (HTTP 4xx/5xx of the main document). */
    val showHttpErrors: Boolean = false,
)

data class SoundSettings(
    /** `none`, `beep`, `error`, `tone:<hz>:<ms>[:<hz>:<ms>…]` or `res:<name>`. */
    val scanOk: String = "none",
    val error: String = "error",
)

data class Profile(
    val id: String,
    val name: String,
    val startUrl: String,
    /** Hosts (and their subdomains) the browser may navigate to. Empty means no restriction. */
    val allowList: List<String> = emptyList(),
    val launchOnStartup: Boolean = false,
    val userAgent: String? = null,
    val clearCacheOnStart: Boolean = false,
    val clearCookiesOnStart: Boolean = false,
    val allowFileUpload: Boolean = false,
    val allowDownloads: Boolean = true,
    val viewport: ViewportSettings = ViewportSettings(),
    val keyboard: KeyboardSettings = KeyboardSettings(),
    val injections: List<Injection> = emptyList(),
    val errorPages: ErrorPageSettings = ErrorPageSettings(),
    val sounds: SoundSettings = SoundSettings(),
    val kiosk: KioskSettings = KioskSettings(),
    val scanner: ScannerSettings = ScannerSettings(),
)

data class GlobalSettings(
    val adminPasswordHash: String? = null,
    val exitPasswordHash: String? = null,
    val hideExit: Boolean = false,
)

/** A file referenced as `res:<name>` and served at `https://appassets.androidplatform.net/res/<name>`. */
class Resource(val bytes: ByteArray, val isText: Boolean) {
    val text: String get() = bytes.toString(Charsets.UTF_8)

    override fun equals(other: Any?) = other is Resource && other.isText == isText && other.bytes.contentEquals(bytes)
    override fun hashCode() = bytes.contentHashCode() * 31 + isText.hashCode()
}

data class AppConfig(
    val global: GlobalSettings = GlobalSettings(),
    val resources: Map<String, Resource> = emptyMap(),
    val profiles: List<Profile> = emptyList(),
) {
    fun profile(id: String?): Profile? = profiles.firstOrNull { it.id == id }

    fun upsert(profile: Profile): AppConfig {
        val index = profiles.indexOfFirst { it.id == profile.id }
        val updated = if (index < 0) profiles + profile else profiles.toMutableList().also { it[index] = profile }
        return copy(profiles = updated)
    }

    fun remove(id: String): AppConfig = copy(profiles = profiles.filterNot { it.id == id })

    /** Resolves `res:<name>` references to the resource text; anything else is returned as inline content. */
    fun content(value: String?): String? {
        value ?: return null
        if (!value.startsWith(RES_PREFIX)) return value
        return resources[value.removePrefix(RES_PREFIX)]?.text
    }

    companion object {
        const val RES_PREFIX = "res:"
        const val DEMO_PROFILE_ID = "demo"
        const val DEMO_URL = "https://appassets.androidplatform.net/assets/demo/index.html"

        fun initial(): AppConfig = AppConfig(
            profiles = listOf(
                Profile(id = DEMO_PROFILE_ID, name = "Demo & Scanner-Test", startUrl = DEMO_URL),
            ),
        )
    }
}
