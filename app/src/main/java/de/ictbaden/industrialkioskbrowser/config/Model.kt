package de.ictbaden.industrialkioskbrowser.config

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

/**
 * Scans broadcast as `<package>.action.BARCODE`. Any app can send this broadcast, so it can also
 * fake scans; AUTO accepts it only on devices without a supported scanner (not Zebra/Honeywell).
 */
enum class GenericIntent(override val key: String) : Keyed {
    AUTO("auto"),
    ON("on"),
    OFF("off");

    companion object {
        fun parse(value: String?): GenericIntent = parseKey(value, AUTO)
    }
}

/**
 * Which scanner reads the barcodes. AUTO uses the scan engine of the device (DataWedge, Honeywell)
 * and falls back to the camera when the device has none (issue #48).
 */
enum class ScanEngine(override val key: String) : Keyed {
    AUTO("auto"),
    HARDWARE("hardware"),
    CAMERA("camera");

    companion object {
        fun parse(value: String?): ScanEngine = parseKey(value, AUTO)
    }
}

data class ScannerSettings(
    val enabled: Boolean = true,
    val terminator: ScanTerminator = ScanTerminator.ENTER,
    val injectIntoFocusedField: Boolean = true,
    val genericIntent: GenericIntent = GenericIntent.AUTO,
    val engine: ScanEngine = ScanEngine.AUTO,
    val camera: CameraSettings = CameraSettings(),
    val data: ScanDataSettings = ScanDataSettings(),
    val ocr: OcrSettings = OcrSettings(),
    val nfc: NfcSettings = NfcSettings(),
)

/** How text recognition picks the text (`scanner.ocr.mode`). */
enum class OcrMode(override val key: String) : Keyed {
    /** Recognised lines in the frame are shown as boxes; a tap takes one. */
    PICK("pick"),

    /** The first line matching [OcrSettings.pattern] is taken automatically. */
    PATTERN("pattern"),
}

/** Clean-up of each recognised line before the length and pattern checks (`scanner.ocr.charset`). */
enum class OcrCharset(override val key: String) : Keyed {
    ANY("any"),

    /** Digits and `, . -` (meter readings, quantities). */
    DIGITS("digits"),

    /** Letters and digits only. */
    ALNUM("alnum"),

    /** Letters and digits, upper case. */
    UPPER("upper"),
}

/** Text recognition (OCR) with the camera (`scanner.ocr`, issue #49). */
data class OcrSettings(
    val enabled: Boolean = true,
    val mode: OcrMode = OcrMode.PICK,
    /** Java regex the whole (cleaned) line must match; required for [OcrMode.PATTERN]. */
    val pattern: String? = null,
    /** The camera opens in text mode instead of barcode mode. */
    val startInOcr: Boolean = false,
    /** Pattern mode: the same text in this many consecutive frames (1–5). */
    val confirmReads: Int = 2,
    val minLength: Int = 0,
    val maxLength: Int = 0,
    val charset: OcrCharset = OcrCharset.ANY,
)

/** What an NFC tag gives (`scanner.nfc.read`). */
enum class NfcRead(override val key: String) : Keyed {
    UID("uid"),
    NDEF("ndef"),

    /** The NDEF text or URI, the UID when the tag has none. */
    NDEF_OR_UID("ndefOrUid"),
}

enum class UidFormat(override val key: String) : Keyed {
    HEX("hex"),
    DECIMAL("decimal"),
}

/** Which NDEF records are entered (`scanner.nfc.ndefRecord`). */
enum class NdefRecordMode(override val key: String) : Keyed {
    FIRST("first"),
    ALL_LINES("allLines"),
}

/** Reading NFC tags (`scanner.nfc`, issue #50). */
data class NfcSettings(
    val enabled: Boolean = true,
    val read: NfcRead = NfcRead.NDEF_OR_UID,
    val uidFormat: UidFormat = UidFormat.HEX,
    /** Reverse byte order (many badge readers show the UID reversed). */
    val uidReversed: Boolean = false,
    /** Between hex bytes, e.g. `:`. */
    val uidSeparator: String = "",
    val ndefRecord: NdefRecordMode = NdefRecordMode.FIRST,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
)

/** Which codes in the camera picture count (`scanner.camera.aim`). */
enum class AimMode(override val key: String) : Keyed {
    /** Codes whose centre lies inside the aiming frame. */
    FRAME("frame"),

    /** Only the code under the crosshair (pick list, for labels with many codes). */
    CENTER("center"),

    /** The whole picture. */
    ANYWHERE("anywhere"),
}

/** Camera scanner settings (`scanner.camera`); not used by the scan engine of the device. */
data class CameraSettings(
    /** Canonical symbology names (see `scanner/Symbologies`); empty = all. */
    val symbologies: List<String> = emptyList(),
    val aim: AimMode = AimMode.FRAME,
    /** The camera stays open and enters every new code. */
    val continuous: Boolean = false,
    /** Continuous mode: the same code is accepted again only after this time. */
    val duplicateDelayMs: Int = 1500,
    /** The same code must be read in this many consecutive frames (1–3). */
    val confirmReads: Int = 1,
    /** Accepted text length, 0 = no limit. */
    val minLength: Int = 0,
    val maxLength: Int = 0,
    /** Zoom ratio when the camera opens (1–4). */
    val zoom: Double = 1.0,
    val torch: Boolean = false,
    /** Also light-on-dark codes. */
    val inverted: Boolean = true,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
    /** Closes after this time without a code; 0 = never. */
    val timeoutSeconds: Int = 60,
)

enum class TextCase(override val key: String) : Keyed {
    NONE("none"),
    UPPER("upper"),
    LOWER("lower"),
}

/** How GS1 codes read by the camera are written. */
enum class Gs1Mode(override val key: String) : Keyed {
    /** Human readable: `(01)…(10)…`. */
    HRI("hri"),

    /** Raw, with the group separator U+001D between variable-length fields. */
    RAW("raw"),
}

enum class RuleAction(override val key: String) : Keyed {
    SEND("send"),
    DROP("drop"),
}

/** One replacement of `scanner.data.replace`; with [regex] `find` is a Java regex and `with` may use `$1`. */
data class Replacement(val find: String, val with: String, val regex: Boolean = false)

/**
 * A rule of `scanner.data.rules`: the first rule whose conditions match a code decides; its non-null
 * fields override the defaults of [ScanDataSettings] for that code.
 */
data class ScanRule(
    val name: String? = null,
    val symbologies: List<String> = emptyList(),
    val minLength: Int = 0,
    val maxLength: Int = 0,
    val startsWith: String? = null,
    /** Java regex that must match the whole (trimmed) text. */
    val pattern: String? = null,
    val action: RuleAction = RuleAction.SEND,
    val prefix: String? = null,
    val suffix: String? = null,
    val case: TextCase? = null,
    val stripLeading: Int? = null,
    val stripTrailing: Int? = null,
    val removeSpaces: Boolean? = null,
    val replace: List<Replacement>? = null,
)

/** Formatting of the barcode text before it is entered (`scanner.data`). */
data class ScanDataSettings(
    /** Plain text before the code. */
    val prefix: String = "",
    /** Plain text after the code; the keys after it stay [ScannerSettings.terminator]. */
    val suffix: String? = null,
    val trim: Boolean = true,
    val removeSpaces: Boolean = false,
    val case: TextCase = TextCase.NONE,
    val stripLeading: Int = 0,
    val stripTrailing: Int = 0,
    val replace: List<Replacement> = emptyList(),
    /** Camera only. */
    val gs1: Gs1Mode = Gs1Mode.HRI,
    /** Replaces the group separator U+001D in any scanned text; null keeps it. */
    val gsReplacement: String? = null,
    val rules: List<ScanRule> = emptyList(),
    /** When [rules] exist and none matches. */
    val unmatched: RuleAction = RuleAction.SEND,
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
    /** Profile color as `#RRGGBB` (tile and toolbar); falls back to the brand color. */
    val color: String? = null,
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

/** App-wide look: shown on the start screen, toolbars and error pages. */
data class Branding(
    /** Title of the start screen; defaults to the app name. */
    val title: String? = null,
    val subtitle: String? = null,
    /** Brand color as `#RRGGBB`. */
    val color: String? = null,
    /** Logo image as `res:<name>` (PNG/JPEG/WebP). */
    val logo: String? = null,
)

data class GlobalSettings(
    val adminPasswordHash: String? = null,
    val exitPasswordHash: String? = null,
    val hideExit: Boolean = false,
    val branding: Branding = Branding(),
    /** `auto` (follow the device) or one of [Languages.SUPPORTED]. */
    val language: String = Languages.AUTO,
)

/** User interface languages (richtlinien/13-sprachen.md); tags as in `global.language`. */
object Languages {
    const val AUTO = "auto"
    val SUPPORTED = listOf("en", "de", "es", "fr", "zh-Hans")
}

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

        /** Configuration before anything was set up; [demoName] comes from the translated resources. */
        fun initial(demoName: String = "Demo & scanner test"): AppConfig = AppConfig(
            profiles = listOf(
                Profile(id = DEMO_PROFILE_ID, name = demoName, startUrl = DEMO_URL),
            ),
        )
    }
}
