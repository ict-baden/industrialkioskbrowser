package de.ictbaden.enterprisebrowser.config

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

    fun parse(text: String): AppConfig {
        val root = try {
            JSONObject(text.trim())
        } catch (e: JSONException) {
            throw ConfigException("Invalid JSON: ${e.message}")
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

    fun write(config: AppConfig): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("global", JSONObject().apply {
            putOpt("adminPasswordHash", config.global.adminPasswordHash)
            putOpt("exitPasswordHash", config.global.exitPasswordHash)
            put("hideExit", config.global.hideExit)
            put("language", config.global.language)
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
        return GlobalSettings(
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
            if (!PasswordHasher.isHash(hash)) throw ConfigException("${key}Hash has an unknown format")
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
        })
    }

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
