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
 * Any app on the device can send this broadcast, and Android does not tell us which one did.
 * So it is only accepted with the admin password (`admin_password`), or – while no password is
 * set – on a device managed by an EMM ([Provisioning.isManaged]). Otherwise a store install
 * could be redirected to a foreign start URL by any other app.
 */
class ConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}$ACTION_SUFFIX") return
        val store = ConfigStore.get(context)
        val adminHash = store.config.global.adminPasswordHash
        if (adminHash != null && !PasswordHasher.verify(intent.getStringExtra(EXTRA_ADMIN_PASSWORD).orEmpty(), adminHash)) {
            deny(context, "admin_password is missing or wrong")
            return
        }
        if (adminHash == null && !Provisioning.isManaged(context)) {
            deny(context, "Refused: no admin password set and the device is not managed by an EMM. Set an admin password or use a configuration file or managed configuration.")
            return
        }
        val json = intent.getStringExtra(EXTRA_CONFIG)
        if (json.isNullOrBlank()) {
            reply(RESULT_INVALID, "Extra \"$EXTRA_CONFIG\" is missing")
            return
        }
        try {
            val imported = store.import(ConfigJson.parse(json))
            Provisioning.reportError(context, "INSTALL_CONFIG", null)
            reply(Activity.RESULT_OK, "${imported.profiles.size} profile(s) installed")
        } catch (e: ConfigException) {
            reply(RESULT_INVALID, e.message ?: "Invalid configuration")
        }
    }

    private fun deny(context: Context, message: String) {
        Provisioning.reportError(context, "INSTALL_CONFIG", message)
        reply(RESULT_DENIED, message)
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
