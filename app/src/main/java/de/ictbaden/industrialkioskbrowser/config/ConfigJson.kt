package de.ictbaden.industrialkioskbrowser.config

import de.ictbaden.industrialkioskbrowser.scanner.Symbologies
import de.ictbaden.industrialkioskbrowser.web.CustomTrust
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

class ConfigException(message: String) : Exception(message)

/**
 * Reads and writes the JSON configuration format documented in `docs/configuration.md`.
 *
 * Passwords may be supplied in plain text (`adminPassword`) and are hashed on import;
 * exported files only ever contain hashes.
 */
object ConfigJson {
    const val VERSION = 2

    /** Plain JSON or a barcode payload packed by [ConfigCodec] (`ICTZ1:`). */
    fun parse(text: String): AppConfig {
        val root = try {
            JSONObject(ConfigCodec.unpack(text).trim())
        } catch (e: JSONException) {
            throw ConfigException("Invalid JSON: ${withoutInput(e)}")
        }
        val version = root.optInt("version", VERSION)
        if (version > VERSION) throw ConfigException("Configuration version $version is not supported")

        val resources = parseResources(root.optJSONObject("resources") ?: JSONObject())
        val global = parseGlobal(root.optJSONObject("global") ?: JSONObject(), resources)
        val profilesJson = root.optJSONArray("profiles") ?: JSONArray()
        val profiles = (0 until profilesJson.length()).map { index ->
            val json = profilesJson.optJSONObject(index)
                ?: throw ConfigException("profiles[$index] is not an object")
            parseProfile(json, index, resources)
        }
        val duplicate = profiles.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) throw ConfigException("Profile ID \"${duplicate.key}\" is used more than once")
        return AppConfig(global, resources, profiles)
    }

    /**
     * Android's `org.json` appends the whole input to its messages ("… at character 12 of {…}"). The
     * input may contain a plain admin password and would end up in the log and the settings screen.
     */
    private fun withoutInput(e: JSONException): String =
        e.message.orEmpty().replace(Regex("(?s)( at character \\d+) of .*"), "$1")

    /** Plain-text `global.adminPassword` of a configuration, to check that it comes from the administrator. */
    fun plainAdminPassword(text: String): String? = try {
        JSONObject(ConfigCodec.unpack(text).trim()).optJSONObject("global")?.optString("adminPassword")?.ifBlank { null }
    } catch (_: JSONException) {
        null
    } catch (_: ConfigException) {
        null
    }

    /**
     * Like [write], but without the values that equal the defaults – [parse] fills them in again.
     * Used for the barcode export, where every character makes the QR code denser.
     */
    fun writeCompact(config: AppConfig): String {
        val root = JSONObject(write(config))
        val defaults = JSONObject(write(AppConfig()))
        val defaultProfile = writeProfile(Profile(id = "", name = "", startUrl = ""))
        prune(root, defaults, keep = setOf("version", "profiles"))
        root.optJSONArray("profiles")?.let { profiles ->
            for (i in 0 until profiles.length()) {
                profiles.optJSONObject(i)?.let { prune(it, defaultProfile, keep = setOf("id", "name", "startUrl")) }
            }
        }
        return root.toString()
    }

    /** Removes every key of [json] whose value equals the one in [defaults]; empty objects go too. */
    private fun prune(json: JSONObject, defaults: JSONObject, keep: Set<String> = emptySet()) {
        val keys = json.keys().asSequence().toList()
        for (key in keys) {
            if (key in keep || !defaults.has(key)) continue
            val value = json.get(key)
            val default = defaults.get(key)
            if (value is JSONObject && default is JSONObject) {
                prune(value, default)
                if (value.length() == 0) json.remove(key)
            } else if (value.javaClass == default.javaClass && value.toString() == default.toString()) {
                json.remove(key)
            }
        }
    }

    fun write(config: AppConfig): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("global", JSONObject().apply {
            putOpt("adminPasswordHash", config.global.adminPasswordHash)
            putOpt("exitPasswordHash", config.global.exitPasswordHash)
            put("hideExit", config.global.hideExit)
            put("language", config.global.language)
            put("trustedCertificates", JSONArray(config.global.trustedCertificates))
            val branding = config.global.branding
            put("branding", JSONObject().apply {
                putOpt("title", branding.title)
                putOpt("subtitle", branding.subtitle)
                putOpt("color", branding.color)
                putOpt("logo", branding.logo)
            })
        })
        root.put("resources", JSONObject().apply {
            config.resources.toSortedMap().forEach { (name, resource) ->
                put(name, JSONObject().apply {
                    if (resource.isText) {
                        put("text", resource.text)
                    } else {
                        put("base64", Base64.getEncoder().encodeToString(resource.bytes))
                    }
                })
            }
        })
        root.put("profiles", JSONArray(config.profiles.map(::writeProfile)))
        return root.toString(2)
    }

    // region parsing

    private fun parseGlobal(json: JSONObject, resources: Map<String, Resource>): GlobalSettings {
        val branding = json.optJSONObject("branding") ?: JSONObject()
        val logo = branding.optNonBlank("logo")
        if (logo != null && (!logo.startsWith(AppConfig.RES_PREFIX) || logo.removePrefix(AppConfig.RES_PREFIX) !in resources)) {
            throw ConfigException("global.branding.logo must reference a resource (res:<name>)")
        }
        val trusted = json.optJSONArray("trustedCertificates")?.let { array ->
            (0 until array.length()).map { i ->
                array.opt(i) as? String ?: throw ConfigException("global.trustedCertificates[$i] must be a string")
            }
        }.orEmpty()
        // Fails with the index of the broken entry.
        CustomTrust.from(trusted, resources)
        return GlobalSettings(
            trustedCertificates = trusted,
            adminPasswordHash = passwordHash(json, "adminPassword"),
            exitPasswordHash = passwordHash(json, "exitPassword"),
            hideExit = json.optBoolean("hideExit", false),
            language = language(json.optNonBlank("language")),
            branding = Branding(
                title = branding.optNonBlank("title"),
                subtitle = branding.optNonBlank("subtitle"),
                color = color(branding.optNonBlank("color"), "global.branding.color"),
                logo = logo,
            ),
        )
    }

    /** `auto` or a supported language tag, matched case-insensitively (`zh-hans` → `zh-Hans`). */
    private fun language(value: String?): String {
        value ?: return Languages.AUTO
        return (listOf(Languages.AUTO) + Languages.SUPPORTED).firstOrNull { it.equals(value, ignoreCase = true) }
            ?: throw ConfigException("global.language: unknown language \"$value\" (${Languages.AUTO}, ${Languages.SUPPORTED.joinToString()})")
    }

    /** Validates `#RRGGBB` (or `#AARRGGBB`) and normalizes it to upper case. */
    private fun color(value: String?, where: String): String? {
        value ?: return null
        if (!value.matches(Regex("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})"))) {
            throw ConfigException("$where must be a color in the format #RRGGBB")
        }
        return value.uppercase()
    }

    private fun passwordHash(json: JSONObject, key: String): String? {
        json.optNonBlank("${key}Hash")?.let { hash ->
            if (!PasswordHasher.isHash(hash)) {
                throw ConfigException("${key}Hash has an unknown format or more than ${PasswordHasher.MAX_ITERATIONS} iterations")
            }
            return hash
        }
        return json.optNonBlank(key)?.let(PasswordHasher::hash)
    }

    private fun parseResources(json: JSONObject): Map<String, Resource> =
        json.keys().asSequence().associateWith { name ->
            if (name.isBlank() || name.split('/').any { it == ".." || it.isEmpty() }) {
                throw ConfigException("Invalid resource name \"$name\"")
            }
            val entry = json.optJSONObject(name)
                ?: throw ConfigException("resources.$name must be an object with \"text\" or \"base64\"")
            when {
                entry.has("text") -> Resource(entry.getString("text").toByteArray(Charsets.UTF_8), isText = true)
                entry.has("base64") -> try {
                    val encoded = entry.getString("base64").filterNot(Char::isWhitespace)
                    Resource(Base64.getDecoder().decode(encoded), isText = false)
                } catch (_: IllegalArgumentException) {
                    throw ConfigException("resources.$name: invalid base64")
                }
                else -> throw ConfigException("resources.$name needs \"text\" or \"base64\"")
            }
        }

    private fun parseProfile(json: JSONObject, index: Int, resources: Map<String, Resource>): Profile {
        val where = "profiles[$index]"
        val startUrl = json.optNonBlank("startUrl") ?: throw ConfigException("$where: startUrl is missing")
        if (!startUrl.startsWith("http://", ignoreCase = true) && !startUrl.startsWith("https://", ignoreCase = true)) {
            throw ConfigException("$where: startUrl must start with http:// or https://")
        }
        val name = json.optNonBlank("name") ?: startUrl
        val id = json.optNonBlank("id") ?: slug(name).ifEmpty { "profile-${index + 1}" }
        val kiosk = json.optJSONObject("kiosk") ?: JSONObject()
        val scanner = json.optJSONObject("scanner") ?: JSONObject()
        val sounds = json.optJSONObject("sounds") ?: JSONObject()
        val errorPages = json.optJSONObject("errorPages") ?: JSONObject()
        val defaults = Profile(id = id, name = name, startUrl = startUrl)
        val profile = defaults.copy(
            color = color(json.optNonBlank("color"), "$where.color"),
            allowList = json.optStringList("allowList"),
            launchOnStartup = json.optBoolean("launchOnStartup", defaults.launchOnStartup),
            userAgent = json.optNonBlank("userAgent"),
            clearCacheOnStart = json.optBoolean("clearCacheOnStart", defaults.clearCacheOnStart),
            clearCookiesOnStart = json.optBoolean("clearCookiesOnStart", defaults.clearCookiesOnStart),
            allowFileUpload = json.optBoolean("allowFileUpload", defaults.allowFileUpload),
            allowDownloads = json.optBoolean("allowDownloads", defaults.allowDownloads),
            viewport = parseViewport(json),
            keyboard = parseKeyboard(json.optJSONObject("keyboard") ?: JSONObject(), where),
            injections = parseInjections(json.optJSONArray("injections") ?: JSONArray(), where),
            errorPages = ErrorPageSettings(
                template = errorPages.optNonBlank("template"),
                showHttpErrors = errorPages.optBoolean("showHttpErrors", false),
            ),
            sounds = SoundSettings(
                scanOk = sounds.optNonBlank("scanOk") ?: defaults.sounds.scanOk,
                error = sounds.optNonBlank("error") ?: defaults.sounds.error,
            ),
            kiosk = KioskSettings(
                hideToolbar = kiosk.optBoolean("hideToolbar", defaults.kiosk.hideToolbar),
                immersive = kiosk.optBoolean("immersive", defaults.kiosk.immersive),
                keepScreenOn = kiosk.optBoolean("keepScreenOn", defaults.kiosk.keepScreenOn),
                orientation = ScreenOrientation.parse(kiosk.optNonBlank("orientation")),
            ),
            scanner = ScannerSettings(
                enabled = scanner.optBoolean("enabled", defaults.scanner.enabled),
                terminator = ScanTerminator.parse(scanner.optNonBlank("terminator"), defaults.scanner.terminator),
                injectIntoFocusedField = scanner.optBoolean(
                    "injectIntoFocusedField",
                    defaults.scanner.injectIntoFocusedField,
                ),
                genericIntent = GenericIntent.parse(scanner.optNonBlank("genericIntent")),
                engine = ScanEngine.parse(scanner.optNonBlank("engine")),
                camera = parseCamera(scanner.optJSONObject("camera") ?: JSONObject(), "$where.scanner.camera"),
                data = parseData(scanner.optJSONObject("data") ?: JSONObject(), "$where.scanner.data"),
                ocr = parseOcr(scanner.optJSONObject("ocr") ?: JSONObject(), "$where.scanner.ocr"),
                nfc = parseNfc(scanner.optJSONObject("nfc") ?: JSONObject(), "$where.scanner.nfc"),
            ),
        )
        checkResourceReferences(profile, resources, where)
        return profile
    }

    private fun parseViewport(profile: JSONObject): ViewportSettings {
        val json = profile.optJSONObject("viewport") ?: JSONObject()
        val defaults = ViewportSettings()
        // Version 1 kept textZoom and pinchZoom on the profile itself.
        val textZoom = if (json.has("textZoom")) json.optInt("textZoom") else profile.optInt("textZoom", defaults.textZoom)
        val pinchZoom = if (json.has("pinchZoom")) json.optBoolean("pinchZoom") else profile.optBoolean("pinchZoom", defaults.pinchZoom)
        val viewport = ViewportSettings(
            mode = parseKey(json.optNonBlank("mode"), defaults.mode),
            width = json.optInt("width", 0).coerceIn(0, 4096),
            initialScale = json.optInt("initialScale", 0).coerceIn(0, 1000),
            fitToScreen = json.optBoolean("fitToScreen", defaults.fitToScreen),
            textZoom = textZoom.coerceIn(25, 400),
            pinchZoom = pinchZoom,
            metaOverride = json.optNonBlank("metaOverride"),
        )
        if (viewport.mode == ViewportMode.FIXED && viewport.width == 0) {
            throw ConfigException("viewport.width is required for mode \"fixed\"")
        }
        return viewport
    }

    private fun parseKeyboard(json: JSONObject, where: String): KeyboardSettings {
        val defaults = KeyboardSettings()
        val remapJson = json.optJSONArray("remap") ?: JSONArray()
        val remap = (0 until remapJson.length()).map { i ->
            val entry = remapJson.optJSONObject(i) ?: throw ConfigException("$where.keyboard.remap[$i] is not an object")
            val key = entry.optNonBlank("key") ?: throw ConfigException("$where.keyboard.remap[$i]: key is missing")
            KeyRemap(key, if (entry.isNull("send")) "" else entry.optString("send", ""))
        }
        val hotkeysJson = json.optJSONObject("hotkeys") ?: JSONObject()
        val hotkeys = hotkeysJson.keys().asSequence().mapNotNull { action ->
            if (HotkeyAction.entries.none { it.key == action }) {
                throw ConfigException("$where.keyboard.hotkeys: unknown action \"$action\"")
            }
            hotkeysJson.optNonBlank(action)?.let { action to it }
        }.toMap()
        return KeyboardSettings(
            soft = parseKey(json.optNonBlank("soft"), defaults.soft),
            showOnFocus = json.optBoolean("showOnFocus", defaults.showOnFocus),
            showOnPageLoad = json.optBoolean("showOnPageLoad", defaults.showOnPageLoad),
            backKey = json.optNonBlank("backKey") ?: defaults.backKey,
            remap = remap,
            hotkeys = hotkeys,
            longPressDigitsAsF = json.optBoolean("longPressDigitsAsF", defaults.longPressDigitsAsF),
        )
    }

    private fun parseInjections(array: JSONArray, where: String): List<Injection> = (0 until array.length()).map { i ->
        val json = array.optJSONObject(i) ?: throw ConfigException("$where.injections[$i] is not an object")
        val match = json.optJSONObject("match") ?: JSONObject()
        val urlMatch = match.optNonBlank("url").orEmpty()
        val injection = Injection(
            urlMatch = urlMatch,
            matchType = parseKey(match.optNonBlank("type"), if (urlMatch.isEmpty()) MatchType.ALL else MatchType.CONTAINS),
            sourceContains = match.optNonBlank("source"),
            timing = parseKey(json.optNonBlank("when"), InjectionTiming.DOCUMENT_START),
            allFrames = json.optNonBlank("frames") == "all",
            css = json.optNonBlank("css"),
            js = json.optNonBlank("js"),
            html = json.optNonBlank("html"),
            htmlPosition = parseKey(json.optNonBlank("position"), HtmlPosition.BODY_END),
        )
        if (injection.css == null && injection.js == null && injection.html == null) {
            throw ConfigException("$where.injections[$i] needs css, js or html")
        }
        if (injection.matchType == MatchType.REGEX) {
            try {
                Regex(injection.urlMatch)
            } catch (e: IllegalArgumentException) {
                throw ConfigException("$where.injections[$i]: invalid regular expression: ${e.message}")
            }
        }
        injection
    }

    private fun parseCamera(json: JSONObject, where: String): CameraSettings {
        val d = CameraSettings()
        return CameraSettings(
            symbologies = symbologies(json, "symbologies", where),
            aim = enumOf(json, "aim", where, d.aim),
            continuous = json.optBoolean("continuous", d.continuous),
            duplicateDelayMs = intIn(json, "duplicateDelayMs", where, d.duplicateDelayMs, 0, 10_000),
            confirmReads = intIn(json, "confirmReads", where, d.confirmReads, 1, 3),
            minLength = intIn(json, "minLength", where, d.minLength, 0, 9999),
            maxLength = intIn(json, "maxLength", where, d.maxLength, 0, 9999),
            zoom = if (json.has("zoom") && !json.isNull("zoom")) {
                json.optDouble("zoom", Double.NaN).takeIf { it in 1.0..4.0 }
                    ?: throw ConfigException("$where.zoom must be a number from 1.0 to 4.0")
            } else {
                d.zoom
            },
            torch = json.optBoolean("torch", d.torch),
            inverted = json.optBoolean("inverted", d.inverted),
            beep = json.optBoolean("beep", d.beep),
            vibrate = json.optBoolean("vibrate", d.vibrate),
            timeoutSeconds = intIn(json, "timeoutSeconds", where, d.timeoutSeconds, 0, 600),
        )
    }

    private fun parseOcr(json: JSONObject, where: String): OcrSettings {
        val d = OcrSettings()
        val mode = enumOf(json, "mode", where, d.mode)
        val pattern = json.optNonBlank("pattern")
        if (pattern != null) regex(pattern, "$where.pattern")
        if (mode == OcrMode.PATTERN && pattern == null) throw ConfigException("$where.pattern is required for mode \"pattern\"")
        return OcrSettings(
            enabled = json.optBoolean("enabled", d.enabled),
            mode = mode,
            pattern = pattern,
            startInOcr = json.optBoolean("startInOcr", d.startInOcr),
            confirmReads = intIn(json, "confirmReads", where, d.confirmReads, 1, 5),
            minLength = intIn(json, "minLength", where, d.minLength, 0, 999),
            maxLength = intIn(json, "maxLength", where, d.maxLength, 0, 999),
            charset = enumOf(json, "charset", where, d.charset),
        )
    }

    private fun parseNfc(json: JSONObject, where: String): NfcSettings {
        val d = NfcSettings()
        return NfcSettings(
            enabled = json.optBoolean("enabled", d.enabled),
            read = enumOf(json, "read", where, d.read),
            uidFormat = enumOf(json, "uidFormat", where, d.uidFormat),
            uidReversed = json.optBoolean("uidReversed", d.uidReversed),
            uidSeparator = text(json, "uidSeparator") ?: d.uidSeparator,
            ndefRecord = enumOf(json, "ndefRecord", where, d.ndefRecord),
            beep = json.optBoolean("beep", d.beep),
            vibrate = json.optBoolean("vibrate", d.vibrate),
        )
    }

    private fun parseData(json: JSONObject, where: String): ScanDataSettings {
        val d = ScanDataSettings()
        val rulesJson = when {
            json.isNull("rules") -> JSONArray()
            else -> json.optJSONArray("rules") ?: throw ConfigException("$where.rules must be an array")
        }
        val rules = (0 until rulesJson.length()).map { i ->
            val rule = rulesJson.optJSONObject(i) ?: throw ConfigException("$where.rules[$i] is not an object")
            parseRule(rule, "$where.rules[$i]")
        }
        return ScanDataSettings(
            prefix = text(json, "prefix") ?: d.prefix,
            suffix = text(json, "suffix"),
            trim = json.optBoolean("trim", d.trim),
            removeSpaces = json.optBoolean("removeSpaces", d.removeSpaces),
            case = enumOf(json, "case", where, d.case),
            stripLeading = intIn(json, "stripLeading", where, d.stripLeading, 0, 999),
            stripTrailing = intIn(json, "stripTrailing", where, d.stripTrailing, 0, 999),
            replace = replacements(json, where) ?: d.replace,
            gs1 = enumOf(json, "gs1", where, d.gs1),
            gsReplacement = text(json, "gsReplacement"),
            rules = rules,
            unmatched = enumOf(json, "unmatched", where, d.unmatched),
        )
    }

    private fun parseRule(json: JSONObject, where: String): ScanRule {
        val pattern = json.optNonBlank("pattern")
        if (pattern != null) regex(pattern, "$where.pattern")
        return ScanRule(
            name = json.optNonBlank("name"),
            symbologies = symbologies(json, "symbologies", where),
            minLength = intIn(json, "minLength", where, 0, 0, 9999),
            maxLength = intIn(json, "maxLength", where, 0, 0, 9999),
            startsWith = text(json, "startsWith")?.ifEmpty { null },
            pattern = pattern,
            action = enumOf(json, "action", where, RuleAction.SEND),
            prefix = text(json, "prefix"),
            suffix = text(json, "suffix"),
            case = if (json.isNull("case")) null else enumOf(json, "case", where, TextCase.NONE),
            stripLeading = if (json.isNull("stripLeading")) null else intIn(json, "stripLeading", where, 0, 0, 999),
            stripTrailing = if (json.isNull("stripTrailing")) null else intIn(json, "stripTrailing", where, 0, 0, 999),
            removeSpaces = if (json.isNull("removeSpaces")) null else json.optBoolean("removeSpaces"),
            replace = replacements(json, where),
        )
    }

    /** `null` when the field is missing or null; an empty array stays empty. */
    private fun replacements(json: JSONObject, where: String): List<Replacement>? {
        if (json.isNull("replace")) return null
        val array = json.optJSONArray("replace") ?: throw ConfigException("$where.replace must be an array")
        return (0 until array.length()).map { i ->
            val entry = array.optJSONObject(i) ?: throw ConfigException("$where.replace[$i] is not an object")
            val find = text(entry, "find")?.ifEmpty { null } ?: throw ConfigException("$where.replace[$i].find is missing")
            val isRegex = entry.optBoolean("regex", false)
            if (isRegex) regex(find, "$where.replace[$i].find")
            Replacement(find, text(entry, "with").orEmpty(), isRegex)
        }
    }

    private fun regex(pattern: String, where: String) {
        try {
            Regex(pattern)
        } catch (e: IllegalArgumentException) {
            throw ConfigException("$where: invalid regular expression: ${e.message?.lineSequence()?.firstOrNull()}")
        }
    }

    private fun symbologies(json: JSONObject, key: String, where: String): List<String> {
        if (json.isNull(key)) return emptyList()
        val array = json.optJSONArray(key) ?: throw ConfigException("$where.$key must be an array")
        return (0 until array.length()).map { i ->
            val name = array.optString(i, "").trim().lowercase()
            if (!Symbologies.isKnown(name)) {
                throw ConfigException("$where.$key: unknown barcode type \"${array.opt(i)}\" (${Symbologies.ALL.joinToString()})")
            }
            name
        }.distinct()
    }

    /** Text as given, also empty or with spaces (prefix, suffix); `null` when missing or null. */
    private fun text(json: JSONObject, key: String): String? = if (json.isNull(key)) null else json.optString(key)

    private fun intIn(json: JSONObject, key: String, where: String, default: Int, min: Int, max: Int): Int {
        if (json.isNull(key)) return default
        val value = json.opt(key)
        val number = (value as? Number)?.takeIf { it.toDouble() == it.toInt().toDouble() }?.toInt()
        if (number == null || number !in min..max) throw ConfigException("$where.$key must be a whole number from $min to $max")
        return number
    }

    private inline fun <reified T> enumOf(json: JSONObject, key: String, where: String, default: T): T where T : Enum<T>, T : Keyed {
        val value = json.optNonBlank(key) ?: return default
        return enumValues<T>().firstOrNull { it.key.equals(value, ignoreCase = true) }
            ?: throw ConfigException("$where.$key: unknown value \"${value}\" (${enumValues<T>().joinToString { it.key }})")
    }

    private fun checkResourceReferences(profile: Profile, resources: Map<String, Resource>, where: String) {
        val references = profile.injections.flatMap { listOf(it.css, it.js, it.html) } +
            listOf(profile.errorPages.template, profile.sounds.scanOk, profile.sounds.error)
        references.filterNotNull()
            .filter { it.startsWith(AppConfig.RES_PREFIX) }
            .map { it.removePrefix(AppConfig.RES_PREFIX) }
            .firstOrNull { it !in resources }
            ?.let { throw ConfigException("$where references unknown resource \"$it\"") }
    }

    // endregion

    // region writing

    private fun writeProfile(profile: Profile) = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("startUrl", profile.startUrl)
        putOpt("color", profile.color)
        put("allowList", JSONArray(profile.allowList))
        put("launchOnStartup", profile.launchOnStartup)
        putOpt("userAgent", profile.userAgent)
        put("clearCacheOnStart", profile.clearCacheOnStart)
        put("clearCookiesOnStart", profile.clearCookiesOnStart)
        put("allowFileUpload", profile.allowFileUpload)
        put("allowDownloads", profile.allowDownloads)
        put("viewport", JSONObject().apply {
            val v = profile.viewport
            put("mode", v.mode.key)
            put("width", v.width)
            put("initialScale", v.initialScale)
            put("fitToScreen", v.fitToScreen)
            put("textZoom", v.textZoom)
            put("pinchZoom", v.pinchZoom)
            putOpt("metaOverride", v.metaOverride)
        })
        put("keyboard", JSONObject().apply {
            val k = profile.keyboard
            put("soft", k.soft.key)
            put("showOnFocus", k.showOnFocus)
            put("showOnPageLoad", k.showOnPageLoad)
            put("backKey", k.backKey)
            put("remap", JSONArray(k.remap.map { JSONObject().put("key", it.key).put("send", it.send) }))
            put("hotkeys", JSONObject(k.hotkeys))
            put("longPressDigitsAsF", k.longPressDigitsAsF)
        })
        put("injections", JSONArray(profile.injections.map(::writeInjection)))
        put("errorPages", JSONObject().apply {
            putOpt("template", profile.errorPages.template)
            put("showHttpErrors", profile.errorPages.showHttpErrors)
        })
        put("sounds", JSONObject().put("scanOk", profile.sounds.scanOk).put("error", profile.sounds.error))
        put("kiosk", JSONObject().apply {
            put("hideToolbar", profile.kiosk.hideToolbar)
            put("immersive", profile.kiosk.immersive)
            put("keepScreenOn", profile.kiosk.keepScreenOn)
            put("orientation", profile.kiosk.orientation.key)
        })
        put("scanner", JSONObject().apply {
            put("enabled", profile.scanner.enabled)
            put("terminator", profile.scanner.terminator.key)
            put("injectIntoFocusedField", profile.scanner.injectIntoFocusedField)
            put("genericIntent", profile.scanner.genericIntent.key)
            put("engine", profile.scanner.engine.key)
            put("camera", writeCamera(profile.scanner.camera))
            put("data", writeData(profile.scanner.data))
            put("ocr", writeOcr(profile.scanner.ocr))
            put("nfc", writeNfc(profile.scanner.nfc))
        })
    }

    private fun writeOcr(o: OcrSettings) = JSONObject().apply {
        put("enabled", o.enabled)
        put("mode", o.mode.key)
        put("pattern", o.pattern ?: JSONObject.NULL)
        put("startInOcr", o.startInOcr)
        put("confirmReads", o.confirmReads)
        put("minLength", o.minLength)
        put("maxLength", o.maxLength)
        put("charset", o.charset.key)
    }

    private fun writeNfc(n: NfcSettings) = JSONObject().apply {
        put("enabled", n.enabled)
        put("read", n.read.key)
        put("uidFormat", n.uidFormat.key)
        put("uidReversed", n.uidReversed)
        put("uidSeparator", n.uidSeparator)
        put("ndefRecord", n.ndefRecord.key)
        put("beep", n.beep)
        put("vibrate", n.vibrate)
    }

    private fun writeCamera(c: CameraSettings) = JSONObject().apply {
        put("symbologies", JSONArray(c.symbologies))
        put("aim", c.aim.key)
        put("continuous", c.continuous)
        put("duplicateDelayMs", c.duplicateDelayMs)
        put("confirmReads", c.confirmReads)
        put("minLength", c.minLength)
        put("maxLength", c.maxLength)
        put("zoom", c.zoom)
        put("torch", c.torch)
        put("inverted", c.inverted)
        put("beep", c.beep)
        put("vibrate", c.vibrate)
        put("timeoutSeconds", c.timeoutSeconds)
    }

    private fun writeData(d: ScanDataSettings) = JSONObject().apply {
        put("prefix", d.prefix)
        put("suffix", d.suffix ?: JSONObject.NULL)
        put("trim", d.trim)
        put("removeSpaces", d.removeSpaces)
        put("case", d.case.key)
        put("stripLeading", d.stripLeading)
        put("stripTrailing", d.stripTrailing)
        put("replace", writeReplacements(d.replace))
        put("gs1", d.gs1.key)
        put("gsReplacement", d.gsReplacement ?: JSONObject.NULL)
        put("rules", JSONArray(d.rules.map(::writeRule)))
        put("unmatched", d.unmatched.key)
    }

    private fun writeRule(r: ScanRule) = JSONObject().apply {
        putOpt("name", r.name)
        if (r.symbologies.isNotEmpty()) put("symbologies", JSONArray(r.symbologies))
        if (r.minLength > 0) put("minLength", r.minLength)
        if (r.maxLength > 0) put("maxLength", r.maxLength)
        putOpt("startsWith", r.startsWith)
        putOpt("pattern", r.pattern)
        put("action", r.action.key)
        putOpt("prefix", r.prefix)
        putOpt("suffix", r.suffix)
        putOpt("case", r.case?.key)
        putOpt("stripLeading", r.stripLeading)
        putOpt("stripTrailing", r.stripTrailing)
        putOpt("removeSpaces", r.removeSpaces)
        r.replace?.let { put("replace", writeReplacements(it)) }
    }

    private fun writeReplacements(list: List<Replacement>) = JSONArray(
        list.map { JSONObject().put("find", it.find).put("with", it.with).put("regex", it.regex) },
    )

    private fun writeInjection(injection: Injection) = JSONObject().apply {
        put("match", JSONObject().apply {
            if (injection.matchType != MatchType.ALL) {
                put("url", injection.urlMatch)
                put("type", injection.matchType.key)
            }
            putOpt("source", injection.sourceContains)
        })
        put("when", injection.timing.key)
        put("frames", if (injection.allFrames) "all" else "main")
        putOpt("css", injection.css)
        putOpt("js", injection.js)
        putOpt("html", injection.html)
        if (injection.html != null) put("position", injection.htmlPosition.key)
    }

    // endregion

    fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun JSONObject.optNonBlank(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, "").trim().ifEmpty { null } }
    }
}
