package de.ictbaden.enterprisebrowser.provisioning

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.ictbaden.enterprisebrowser.config.ConfigException
import de.ictbaden.enterprisebrowser.config.ConfigJson
import de.ictbaden.enterprisebrowser.config.ConfigStore
import de.ictbaden.enterprisebrowser.config.PasswordHasher

/**
 * Installs a configuration sent as broadcast (e.g. by StageNow or an EMM intent action):
 * `adb shell am broadcast -a <package>.action.INSTALL_CONFIG --es config '<json>'`.
 * Once an admin password is set, the broadcast must carry it in `admin_password`.
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}$ACTION_SUFFIX") return
        val store = ConfigStore.get(context)
        val adminHash = store.config.global.adminPasswordHash
        if (adminHash != null && !PasswordHasher.verify(intent.getStringExtra(EXTRA_ADMIN_PASSWORD).orEmpty(), adminHash)) {
            reply(RESULT_DENIED, "admin_password is missing or wrong")
            return
        }
        val json = intent.getStringExtra(EXTRA_CONFIG)
        if (json.isNullOrBlank()) {
            reply(RESULT_INVALID, "Extra \"$EXTRA_CONFIG\" is missing")
            return
        }
        try {
            val imported = store.import(ConfigJson.parse(json))
            reply(Activity.RESULT_OK, "${imported.profiles.size} profile(s) installed")
        } catch (e: ConfigException) {
            reply(RESULT_INVALID, e.message ?: "Invalid configuration")
        }
    }

    private fun reply(code: Int, message: String) {
        Log.i(TAG, "INSTALL_CONFIG: $message")
        if (isOrderedBroadcast) setResult(code, message, null)
    }

    companion object {
        const val ACTION_SUFFIX = ".action.INSTALL_CONFIG"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_ADMIN_PASSWORD = "admin_password"
        private const val RESULT_DENIED = 2
        private const val RESULT_INVALID = 3
        private const val TAG = "ConfigReceiver"
    }
}
