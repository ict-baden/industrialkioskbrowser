package de.ictbaden.industrialkioskbrowser.provisioning

import android.content.Context
import androidx.core.content.edit
import de.ictbaden.industrialkioskbrowser.config.PasswordHasher

/**
 * Password check for configurations from other apps (broadcast, file in the app folder) and for
 * the password dialogs of the app itself. After [MAX_FAILURES] wrong passwords in a row every
 * attempt of that [Source] is refused for [LOCKOUT_MS], so neither a foreign app nor a person at
 * the device can try passwords at full speed. Each source has its own counter: an app that spams
 * wrong broadcasts cannot block the configuration file or the dialog.
 */
object AdminGate {
    const val MAX_FAILURES = 5
    const val LOCKOUT_MS = 15 * 60 * 1000L
    private const val PREFS = "admin_gate"

    enum class Source(val key: String) {
        BROADCAST("broadcast"),
        FILE("file"),

        /** Admin and exit password dialogs; they share one counter. */
        DIALOG("dialog"),
    }

    enum class Result { OK, WRONG, LOCKED }

    fun isLocked(context: Context, source: Source, now: Long = System.currentTimeMillis()) = remainingMs(context, source, now) > 0

    /** How long [source] stays locked, `0` when it is not. */
    fun remainingMs(context: Context, source: Source, now: Long = System.currentTimeMillis()): Long {
        val lockedUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("${source.key}_locked_until", 0)
        // A lock further in the future than LOCKOUT_MS comes from a clock that was ahead: ignore it.
        return if (now < lockedUntil && lockedUntil - now <= LOCKOUT_MS) lockedUntil - now else 0
    }

    fun check(context: Context, source: Source, password: String?, hash: String, now: Long = System.currentTimeMillis()): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val failuresKey = "${source.key}_failures"
        val lockedKey = "${source.key}_locked_until"
        if (isLocked(context, source, now)) return Result.LOCKED
        if (PasswordHasher.verify(password.orEmpty(), hash)) {
            prefs.edit { remove(failuresKey).remove(lockedKey) }
            return Result.OK
        }
        val failures = prefs.getInt(failuresKey, 0) + 1
        prefs.edit {
            if (failures >= MAX_FAILURES) {
                putLong(lockedKey, now + LOCKOUT_MS)
                remove(failuresKey)
            } else {
                putInt(failuresKey, failures)
            }
        }
        return Result.WRONG
    }

    fun message(result: Result) = when (result) {
        Result.OK -> null
        Result.WRONG -> "admin_password is missing or wrong"
        Result.LOCKED -> "Refused: too many wrong admin passwords, locked for ${LOCKOUT_MS / 60_000} minutes"
    }
}
