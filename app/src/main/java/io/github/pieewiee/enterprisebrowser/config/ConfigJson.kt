package io.github.pieewiee.enterprisebrowser.config

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ConfigException(message: String) : Exception(message)

/**
 * Reads and writes the JSON configuration format documented in `docs/configuration.md`.
 *
 * Passwords may be supplied in plain text (`adminPassword`) and are hashed on import;
 * exported files only ever contain hashes.
 */
object ConfigJson {
    const val VERSION = 1

    fun parse(text: String): AppConfig {
        val root = try {
            JSONObject(text.trim())
        } catch (e: JSONException) {
            throw ConfigException("Ungültiges JSON: ${e.message}")
        }
        val version = root.optInt("version", VERSION)
        if (version > VERSION) throw ConfigException("Konfigurationsversion $version wird nicht unterstützt")

        val global = parseGlobal(root.optJSONObject("global") ?: JSONObject())
        val profilesJson = root.optJSONArray("profiles") ?: JSONArray()
        val profiles = (0 until profilesJson.length()).map { index ->
            val json = profilesJson.optJSONObject(index)
                ?: throw ConfigException("profiles[$index] ist kein Objekt")
            parseProfile(json, index)
        }
        val duplicate = profiles.groupBy { it.id }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) throw ConfigException("Profil-ID \"${duplicate.key}\" ist doppelt vergeben")
        return AppConfig(global, profiles)
    }

    fun write(config: AppConfig): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("global", JSONObject().apply {
            putOpt("adminPasswordHash", config.global.adminPasswordHash)
            putOpt("exitPasswordHash", config.global.exitPasswordHash)
            put("hideExit", config.global.hideExit)
        })
        root.put("profiles", JSONArray(config.profiles.map(::writeProfile)))
        return root.toString(2)
    }

    private fun parseGlobal(json: JSONObject) = GlobalSettings(
        adminPasswordHash = passwordHash(json, "adminPassword"),
        exitPasswordHash = passwordHash(json, "exitPassword"),
        hideExit = json.optBoolean("hideExit", false),
    )

    private fun passwordHash(json: JSONObject, key: String): String? {
        json.optNonBlank("${key}Hash")?.let { hash ->
            if (!PasswordHasher.isHash(hash)) throw ConfigException("${key}Hash hat ein unbekanntes Format")
            return hash
        }
        return json.optNonBlank(key)?.let(PasswordHasher::hash)
    }

    private fun parseProfile(json: JSONObject, index: Int): Profile {
        val startUrl = json.optNonBlank("startUrl")
            ?: throw ConfigException("profiles[$index]: startUrl fehlt")
        if (!startUrl.startsWith("http://", ignoreCase = true) && !startUrl.startsWith("https://", ignoreCase = true)) {
            throw ConfigException("profiles[$index]: startUrl muss mit http:// oder https:// beginnen")
        }
        val name = json.optNonBlank("name") ?: startUrl
        val id = json.optNonBlank("id") ?: slug(name).ifEmpty { "profile-${index + 1}" }
        val kiosk = json.optJSONObject("kiosk") ?: JSONObject()
        val scanner = json.optJSONObject("scanner") ?: JSONObject()
        val defaults = Profile(id = id, name = name, startUrl = startUrl)
        return defaults.copy(
            allowList = json.optStringList("allowList"),
            launchOnStartup = json.optBoolean("launchOnStartup", defaults.launchOnStartup),
            userAgent = json.optNonBlank("userAgent"),
            textZoom = json.optInt("textZoom", defaults.textZoom).coerceIn(25, 400),
            clearCacheOnStart = json.optBoolean("clearCacheOnStart", defaults.clearCacheOnStart),
            clearCookiesOnStart = json.optBoolean("clearCookiesOnStart", defaults.clearCookiesOnStart),
            allowFileUpload = json.optBoolean("allowFileUpload", defaults.allowFileUpload),
            allowDownloads = json.optBoolean("allowDownloads", defaults.allowDownloads),
            pinchZoom = json.optBoolean("pinchZoom", defaults.pinchZoom),
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
    }

    private fun writeProfile(profile: Profile) = JSONObject().apply {
        put("id", profile.id)
        put("name", profile.name)
        put("startUrl", profile.startUrl)
        put("allowList", JSONArray(profile.allowList))
        put("launchOnStartup", profile.launchOnStartup)
        putOpt("userAgent", profile.userAgent)
        put("textZoom", profile.textZoom)
        put("clearCacheOnStart", profile.clearCacheOnStart)
        put("clearCookiesOnStart", profile.clearCookiesOnStart)
        put("allowFileUpload", profile.allowFileUpload)
        put("allowDownloads", profile.allowDownloads)
        put("pinchZoom", profile.pinchZoom)
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

    fun slug(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun JSONObject.optNonBlank(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it, "").trim().ifEmpty { null } }
    }
}
