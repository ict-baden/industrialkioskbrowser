package de.ictbaden.industrialkioskbrowser.provisioning

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.GlobalSettings
import de.ictbaden.industrialkioskbrowser.config.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProvisioningTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ConfigStore.get(context)
    private val file get() = Provisioning.appFolderFile(context)!!

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("admin_gate", Context.MODE_PRIVATE).edit().clear().commit()
        file.delete()
    }

    @Test
    fun `app folder file is applied without password from Android 11 on`() {
        write("""{"profiles":[{"startUrl":"$URL"}]}""")
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
    }

    @Test
    fun `app folder file is refused before Android 11 on an unmanaged device without password`() {
        val refusal = Provisioning.appFolderRefusal(context, "{}", sdk = Build.VERSION_CODES.Q)
        assertNotNull(refusal)
        assertTrue(refusal!!.contains("Android 11"))
    }

    @Test
    fun `app folder file needs the current admin password and keeps it`() {
        val hash = PasswordHasher.hash("secret")
        store.import(AppConfig.initial("Demo").copy(global = GlobalSettings(adminPasswordHash = hash)))

        write("""{"global":{"adminPassword":"other"},"profiles":[{"startUrl":"$URL"}]}""")
        Provisioning.applyAll(context)
        assertNotEquals(URL, store.config.profiles.first().startUrl)
        assertTrue(Provisioning.lastError(context)!!.contains("admin_password"))

        write("""{"global":{"adminPassword":"secret"},"profiles":[{"startUrl":"$URL", "name":"changed"}]}""")
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
        assertEquals(hash, store.config.global.adminPasswordHash)
        assertNull(Provisioning.lastError(context))
        assertEquals("file with plain password is deleted", false, file.exists())
    }

    @Test
    fun `wrong passwords lock the gate`() {
        val hash = PasswordHasher.hash("secret")
        val broadcast = AdminGate.Source.BROADCAST
        repeat(AdminGate.MAX_FAILURES) {
            assertEquals(AdminGate.Result.WRONG, AdminGate.check(context, broadcast, "wrong", hash, now = 1_000))
        }
        assertEquals(AdminGate.Result.LOCKED, AdminGate.check(context, broadcast, "secret", hash, now = 2_000))
        // Broadcast spam does not block the configuration file.
        assertEquals(AdminGate.Result.OK, AdminGate.check(context, AdminGate.Source.FILE, "secret", hash, now = 2_000))
        assertEquals(AdminGate.Result.OK, AdminGate.check(context, broadcast, "secret", hash, now = 2_000 + AdminGate.LOCKOUT_MS))
    }

    @Test
    fun `a lock from a clock that was far ahead is ignored`() {
        val hash = PasswordHasher.hash("secret")
        repeat(AdminGate.MAX_FAILURES) { AdminGate.check(context, AdminGate.Source.FILE, "wrong", hash, now = 10 * AdminGate.LOCKOUT_MS) }
        assertTrue(AdminGate.isLocked(context, AdminGate.Source.FILE, now = 10 * AdminGate.LOCKOUT_MS))
        assertEquals(false, AdminGate.isLocked(context, AdminGate.Source.FILE, now = 1_000))
    }

    @Test
    fun `password hashes with absurd iteration counts are rejected`() {
        val hash = PasswordHasher.hash("secret")
        assertTrue(PasswordHasher.verify("secret", hash))
        val huge = hash.replaceFirst("\$60000\$", "\$2000000000\$")
        assertEquals(false, PasswordHasher.isHash(huge))
        assertEquals(false, PasswordHasher.verify("secret", huge))
    }

    @Test
    fun `an invalid file does not leak its text into the provisioning error`() {
        // Android's org.json puts the whole input into the exception message, including a plain admin password.
        write("""{"global":{"adminPassword":"S3cretPW"}, broken""")
        Provisioning.applyAll(context)
        val error = Provisioning.lastError(context)!!
        assertTrue(error, error.contains("Invalid JSON"))
        assertEquals(false, error.contains("S3cretPW"))
    }

    private fun write(json: String) {
        file.parentFile!!.mkdirs()
        file.writeText(json)
        // Each write must look new to the change detection (modification time and size).
        file.setLastModified(System.currentTimeMillis() + json.length * 1000L)
    }

    private companion object {
        const val URL = "https://wms.example.com"
    }
}
