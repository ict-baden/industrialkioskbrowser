package de.ictbaden.pforte.provisioning

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import de.ictbaden.pforte.config.ConfigException
import de.ictbaden.pforte.config.ConfigJson
import de.ictbaden.pforte.config.ConfigStore
import de.ictbaden.pforte.config.KioskSettings
import de.ictbaden.pforte.config.PasswordHasher
import de.ictbaden.pforte.config.Profile
import java.io.File

/**
 * Applies configuration pushed by an EMM: Android managed configurations
 * (`res/xml/app_restrictions.xml`) and config files dropped into the app's external files
 * directory or Zebra's persistent `/enterprise/usr`. Each source is applied once per change.
 *
 * Managed configuration and `/enterprise/usr` can only be written by the EMM, MX/StageNow or adb
 * and are trusted. The app folder (`Android/data/<package>/files/`) is writable by adb and, up to
 * Android 10, by any app with storage permission – a file there is checked like a broadcast
 * ([appFolderRefusal]).
 */
object Provisioning {
    const val CONFIG_FILE_NAME = "pforte.json"
    const val MANAGED_PROFILE_ID = "managed"
    private const val TAG = "Provisioning"
    private const val PREFS = "provisioning"
    private const val KEY_LAST_ERROR = "last_error"

    fun applyAll(context: Context) {
        applyManagedConfig(context)
        applyFiles(context)
    }

    /** Only the configuration files; an unchanged file costs a `stat`, so an open session checks them regularly. */
    fun applyFiles(context: Context) {
        appFolderFile(context)?.let { applyConfigFile(context, it, trusted = false) }
        applyConfigFile(context, enterpriseFile, trusted = true)
    }

    fun appFolderFile(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, CONFIG_FILE_NAME) }

    private val enterpriseFile = File("/enterprise/usr", CONFIG_FILE_NAME)

    /**
     * Why a file from the app folder is refused, or `null` when it may be applied: once an admin
     * password is set the file has to contain it as `global.adminPassword` (plain text). Without a
     * password it is accepted on managed devices and from Android 11 on, where only adb (USB, i.e.
     * physical access) can write into the app folder.
     */
    @VisibleForTesting
    internal fun appFolderRefusal(context: Context, text: String, sdk: Int = Build.VERSION.SDK_INT): String? {
        val adminHash = ConfigStore.get(context).config.global.adminPasswordHash
            ?: return if (sdk >= Build.VERSION_CODES.R || isManaged(context)) null
            else "Refused: no admin password set, and before Android 11 other apps can write this file. Set an admin password and add it to the file as global.adminPassword, or use /enterprise/usr or the managed configuration."
        return AdminGate.message(AdminGate.check(context, AdminGate.Source.FILE, ConfigJson.plainAdminPassword(text), adminHash))
    }

    /**
     * True when an EMM manages this app: it has a managed configuration, or the device or work
     * profile has an owner (Android Enterprise). Only then may other apps configure it without the
     * admin password, see [ConfigReceiver].
     */
    fun isManaged(context: Context): Boolean {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions
        if (restrictions != null && !restrictions.isEmpty) return true
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins.orEmpty().any { dpm.isDeviceOwnerApp(it.packageName) || dpm.isProfileOwnerApp(it.packageName) }
    }

    /** Shown in the settings: StageNow and EMM scripts usually do not show broadcast results. `null` clears it. */
    fun reportError(context: Context, source: String, message: String?) = prefs(context).edit {
        if (message == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, "$source: $message")
    }

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

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

    /** A refused file is only tried again when it changes, so it cannot use up the password attempts. */
    private fun applyConfigFile(context: Context, file: File, trusted: Boolean) {
        if (!file.canRead()) return
        val fingerprint = "${file.lastModified()}:${file.length()}"
        val key = "file:${file.path}"
        if (fingerprint == prefs(context).getString(key, null)) return
        // While the gate is locked the file is not even checked; it is read again once the lock has expired.
        if (!trusted && AdminGate.isLocked(context, AdminGate.Source.FILE)) {
            reportError(context, file.path, AdminGate.message(AdminGate.Result.LOCKED))
            return
        }
        var delete = false
        runSource(context, file.path) {
            val text = file.readText()
            val imported = ConfigJson.parse(text)
            val store = ConfigStore.get(context)
            if (trusted) {
                store.import(imported)
            } else {
                appFolderRefusal(context, text)?.let { throw ConfigException(it) }
                // A file from the app folder never replaces an admin password that is already set.
                val adminHash = store.config.global.adminPasswordHash ?: imported.global.adminPasswordHash
                store.import(imported.copy(global = imported.global.copy(adminPasswordHash = adminHash)))
                // Up to Android 10 other apps can read the app folder: don't leave a plain password there.
                delete = ConfigJson.plainAdminPassword(text) != null
            }
        }
        if (delete && file.delete()) {
            Log.i(TAG, "Deleted ${file.path}, it contained the admin password in plain text")
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
