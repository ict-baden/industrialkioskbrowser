package de.ictbaden.industrialkioskbrowser.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.PasswordHasher
import de.ictbaden.industrialkioskbrowser.provisioning.AdminGate
import de.ictbaden.industrialkioskbrowser.web.ErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File

/** The admin and exit password dialogs lock after five wrong entries, like broadcasts and files. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PasswordDialogTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val hash = PasswordHasher.hash("right")
    private lateinit var activity: MainActivity
    private var unlocked = 0

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("admin_gate", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowDialog.reset()
        activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    @Test
    fun `the right password unlocks and clears earlier failures`() {
        repeat(AdminGate.MAX_FAILURES - 1) { assertTrue(enter("wrong")) }
        assertTrue(enter("right"))
        assertEquals(1, unlocked)
        // The counter starts again: four more wrong entries do not lock.
        repeat(AdminGate.MAX_FAILURES - 1) { assertTrue(enter("wrong")) }
        assertFalse(AdminGate.isLocked(context, AdminGate.Source.DIALOG))
    }

    @Test
    fun `five wrong entries lock the dialogs, also for the right password`() {
        repeat(AdminGate.MAX_FAILURES) { assertTrue("dialog shown", enter("wrong")) }
        assertTrue(AdminGate.isLocked(context, AdminGate.Source.DIALOG))
        assertTrue(ShadowToast.getTextOfLatestToast(), ShadowToast.getTextOfLatestToast().contains(ErrorCodes.PASSWORD_LOCKED))

        assertFalse("no dialog while locked", enter("right"))
        assertEquals(0, unlocked)
        assertTrue(ShadowToast.getTextOfLatestToast().contains(ErrorCodes.PASSWORD_LOCKED))
    }

    @Test
    fun `the lock ends after 15 minutes and does not touch broadcasts and files`() {
        val now = System.currentTimeMillis()
        repeat(AdminGate.MAX_FAILURES) { AdminGate.check(context, AdminGate.Source.DIALOG, "wrong", hash, now) }
        assertEquals(AdminGate.LOCKOUT_MS, AdminGate.remainingMs(context, AdminGate.Source.DIALOG, now))
        assertEquals(AdminGate.Result.LOCKED, AdminGate.check(context, AdminGate.Source.DIALOG, "right", hash, now + 60_000))
        assertFalse(AdminGate.isLocked(context, AdminGate.Source.BROADCAST, now))
        assertFalse(AdminGate.isLocked(context, AdminGate.Source.FILE, now))
        assertEquals(0, AdminGate.remainingMs(context, AdminGate.Source.DIALOG, now + AdminGate.LOCKOUT_MS))
        assertEquals(AdminGate.Result.OK, AdminGate.check(context, AdminGate.Source.DIALOG, "right", hash, now + AdminGate.LOCKOUT_MS))
    }

    @Test
    fun `without a password nothing is asked`() {
        Dialogs.requirePassword(activity, null, "Admin") { unlocked++ }
        assertEquals(1, unlocked)
        assertNull(ShadowDialog.getLatestDialog())
    }

    /** Opens the dialog and confirms [password]; false when no dialog appeared. */
    private fun enter(password: String): Boolean {
        ShadowDialog.reset()
        Dialogs.requirePassword(activity, hash, "Admin") { unlocked++ }
        shadowOf(Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as? AlertDialog ?: return false
        editText(dialog.window!!.decorView)!!.setText(password)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return true
    }

    private fun editText(view: View): EditText? = when (view) {
        is EditText -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { editText(view.getChildAt(it)) }
        else -> null
    }
}
