package de.ictbaden.pforte.config

import android.content.Context
import de.ictbaden.pforte.R
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File

/** Process-wide holder of the persisted [AppConfig] (`files/config.json`). */
class ConfigStore private constructor(private val file: File, private val demoName: String) {

    @Volatile
    var config: AppConfig = load()
        private set

    @Synchronized
    fun update(transform: (AppConfig) -> AppConfig): AppConfig {
        val updated = transform(config)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(ConfigJson.write(updated))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save the configuration" }
        }
        config = updated
        return updated
    }

    /** Replaces the configuration with [imported], keeping existing passwords the import does not set. */
    fun import(imported: AppConfig): AppConfig = update { current ->
        imported.copy(
            global = imported.global.copy(
                adminPasswordHash = imported.global.adminPasswordHash ?: current.global.adminPasswordHash,
                exitPasswordHash = imported.global.exitPasswordHash ?: current.global.exitPasswordHash,
            ),
        )
    }

    private fun load(): AppConfig {
        if (!file.exists()) return AppConfig.initial(demoName)
        return try {
            ConfigJson.parse(file.readText())
        } catch (e: ConfigException) {
            Log.e(TAG, "Stored configuration is invalid, using defaults", e)
            AppConfig.initial(demoName)
        }
    }

    companion object {
        private const val TAG = "ConfigStore"

        @Volatile
        private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore = instance ?: synchronized(this) {
            instance ?: ConfigStore(
                File(context.applicationContext.filesDir, "config.json"),
                context.getString(R.string.demo_profile_name),
            ).also { instance = it }
        }

        /** Forgets the cached store so the next [get] reads the file again (tests). */
        @VisibleForTesting
        internal fun reset() {
            instance = null
        }
    }
}
