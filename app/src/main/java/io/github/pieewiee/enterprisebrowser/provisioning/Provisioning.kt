package io.github.pieewiee.enterprisebrowser.provisioning

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import io.github.pieewiee.enterprisebrowser.config.ConfigException
import io.github.pieewiee.enterprisebrowser.config.ConfigJson
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.KioskSettings
import io.github.pieewiee.enterprisebrowser.config.PasswordHasher
import io.github.pieewiee.enterprisebrowser.config.Profile
import java.io.File

/**
 * Applies configuration pushed by an EMM: Android managed configurations
 * (`res/xml/app_restrictions.xml`) and config files dropped into the app's external files
 * directory or Zebra's persistent `/enterprise/usr`. Each source is applied once per change.
 */
object Provisioning {
    const val CONFIG_FILE_NAME = "enterprisebrowser.json"
    const val MANAGED_PROFILE_ID = "managed"
    private const val TAG = "Provisioning"
    private const val PREFS = "provisioning"
    private const val KEY_LAST_ERROR = "last_error"

    fun applyAll(context: Context) {
        applyManagedConfig(context)
        configFiles(context).forEach { applyConfigFile(context, it) }
    }

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    fun configFiles(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let { File(it, CONFIG_FILE_NAME) },
        File("/enterprise/usr", CONFIG_FILE_NAME),
    )

    private fun applyManagedConfig(context: Context) {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions ?: return
        val fingerprint = fingerprint(restrictions)
        if (fingerprint == prefs(context).getString("managed", null)) return
        runSource(context, "Managed Configuration") {
            val store = ConfigStore.get(context)
            val configJson = restrictions.getString("config_json")?.trim()
            val startUrl = restrictions.getString("start_url")?.trim()
            when {
                !configJson.isNullOrEmpty() -> store.import(ConfigJson.parse(configJson))
                !startUrl.isNullOrEmpty() -> {
                    if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://")) {
                        throw ConfigException("start_url must start with http:// or https://")
                    }
                    val kiosk = restrictions.getBoolean("kiosk_mode", false)
                    val profile = Profile(
                        id = MANAGED_PROFILE_ID,
                        name = restrictions.getString("profile_name")?.trim()?.ifEmpty { null } ?: "Managed",
                        startUrl = startUrl,
                        launchOnStartup = true,
                        kiosk = KioskSettings(hideToolbar = kiosk, immersive = kiosk),
                    )
                    store.update { config -> config.upsert(profile).copy(global = config.global.copy(hideExit = kiosk)) }
                }
            }
            restrictions.getString("admin_password")?.takeIf { it.isNotEmpty() }?.let { password ->
                store.update { it.copy(global = it.global.copy(adminPasswordHash = PasswordHasher.hash(password))) }
            }
        }
        prefs(context).edit { putString("managed", fingerprint) }
    }

    private fun applyConfigFile(context: Context, file: File) {
        if (!file.canRead()) return
        val fingerprint = "${file.lastModified()}:${file.length()}"
        val key = "file:${file.path}"
        if (fingerprint == prefs(context).getString(key, null)) return
        runSource(context, file.path) {
            ConfigStore.get(context).import(ConfigJson.parse(file.readText()))
        }
        prefs(context).edit { putString(key, fingerprint) }
    }

    private fun runSource(context: Context, source: String, block: () -> Unit) {
        try {
            block()
            prefs(context).edit { remove(KEY_LAST_ERROR) }
            Log.i(TAG, "Applied configuration from $source")
        } catch (e: Exception) {
            Log.e(TAG, "Configuration from $source rejected", e)
            prefs(context).edit { putString(KEY_LAST_ERROR, "$source: ${e.message}") }
        }
    }

    private fun fingerprint(bundle: Bundle): String =
        bundle.keySet().sorted().joinToString("|") { key ->
            @Suppress("DEPRECATION")
            "$key=${bundle.get(key)}"
        }.hashCode().toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
