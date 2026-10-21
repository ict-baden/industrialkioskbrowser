package de.ictbaden.pforte.provisioning

import android.content.Context
import androidx.core.content.edit
import de.ictbaden.pforte.config.PasswordHasher

/**
 * Admin password check for configurations from other apps (broadcast, file in the app folder).
 * After [MAX_FAILURES] wrong passwords in a row every attempt of that [Source] is refused for
 * [LOCKOUT_MS], so a foreign app cannot try passwords at full speed. Each source has its own
 * counter: an app that spams wrong broadcasts cannot block the configuration file.
 */
object AdminGate {
    const val MAX_FAILURES = 5
    const val LOCKOUT_MS = 15 * 60 * 1000L
    private const val PREFS = "admin_gate"

    enum class Source(val key: String) { BROADCAST("broadcast"), FILE("file") }

    enum class Result { OK, WRONG, LOCKED }

    fun isLocked(context: Context, source: Source, now: Long = System.currentTimeMillis()): Boolean {
        val lockedUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("${source.key}_locked_until", 0)
        // A lock further in the future than LOCKOUT_MS comes from a clock that was ahead: ignore it.
        return now < lockedUntil && lockedUntil - now <= LOCKOUT_MS
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
