package de.ictbaden.industrialkioskbrowser.provisioning

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigJson
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
    private val enterpriseFile get() = File(context.cacheDir, "enterprise-usr/${Provisioning.CONFIG_FILE_NAME}")

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("admin_gate", Context.MODE_PRIVATE).edit().clear().commit()
        file.delete()
        enterpriseFile.delete()
        Provisioning.enterpriseFile = enterpriseFile
    }

    @Test
    fun `enterprise folder file sets the first admin password and is checked from then on`() {
        // First provisioning (StageNow): nothing is protected yet, the file is accepted and sets the password.
        write("""{"global":{"adminPassword":"first"},"profiles":[{"startUrl":"$URL"}]}""", enterpriseFile)
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
        val hash = store.config.global.adminPasswordHash
        assertTrue(PasswordHasher.verify("first", hash!!))
        assertEquals("file with plain password is deleted", false, enterpriseFile.exists())

        // /enterprise/usr is world-writable on Zebra devices: another app drops its own configuration and password.
        write("""{"global":{"adminPassword":"attacker"},"profiles":[{"startUrl":"https://evil.example"}]}""", enterpriseFile)
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
        assertEquals(hash, store.config.global.adminPasswordHash)
        assertTrue(Provisioning.lastError(context)!!.contains("admin_password"))

        // The same without any password, and with a hash of its own.
        val foreignHash = PasswordHasher.hash("attacker")
        write("""{"global":{"adminPasswordHash":"$foreignHash"},"profiles":[{"startUrl":"https://evil.example"}]}""", enterpriseFile)
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
        assertEquals(hash, store.config.global.adminPasswordHash)

        // The administrator's file names the current password: applied, the password stays.
        write("""{"global":{"adminPassword":"first"},"profiles":[{"startUrl":"$URL/new"}]}""", enterpriseFile)
        Provisioning.applyAll(context)
        assertEquals("$URL/new", store.config.profiles.single().startUrl)
        assertEquals(hash, store.config.global.adminPasswordHash)
        assertNull(Provisioning.lastError(context))
    }

    @Test
    fun `enterprise folder file with only a hash stays on the device`() {
        // For a file that should survive an enterprise reset: no plain password in it, so nothing to delete.
        val hash = PasswordHasher.hash("first")
        write("""{"global":{"adminPasswordHash":"$hash"},"profiles":[{"startUrl":"$URL"}]}""", enterpriseFile)
        Provisioning.applyAll(context)
        assertEquals(URL, store.config.profiles.single().startUrl)
        assertEquals(hash, store.config.global.adminPasswordHash)
        assertTrue(enterpriseFile.exists())
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

    @Test
    fun `a file that is valid JSON but no object does not leak its text either`() {
        // org.json then reports "Value <the whole value> of type … cannot be converted to JSONObject".
        for (text in listOf(
            """[{"global":{"adminPassword":"S3cretPW"},"profiles":[]}]""", // wrapped in an array
            """"{\"global\":{\"adminPassword\":\"S3cretPW\"}}"""", // escaped once too often: a JSON string
            """adminPassword=S3cretPW""", // not JSON at all
        )) {
            write(text)
            Provisioning.applyAll(context)
            val error = Provisioning.lastError(context)!!
            assertTrue(error, error.contains("Invalid JSON"))
            assertEquals(error, false, error.contains("S3cretPW"))
        }
    }

    @Test
    fun `a deeply nested or huge file is refused instead of crashing`() {
        write("[".repeat(50_000))
        Provisioning.applyAll(context)
        assertTrue(Provisioning.lastError(context)!!.contains("Invalid JSON"))
        assertEquals(AppConfig.DEMO_PROFILE_ID, store.config.profiles.single().id)

        write(" ".repeat(ConfigJson.MAX_LENGTH + 1))
        Provisioning.applyAll(context)
        assertTrue(Provisioning.lastError(context)!!.contains("larger than"))
    }

    @Test
    fun `a file too large to read is refused before it is read`() {
        // 3 GB, sparse: reading it throws OutOfMemoryError (an Error, not caught as a rejected configuration),
        // and because the fingerprint is stored after the import the app would crash again on every start.
        file.parentFile!!.mkdirs()
        java.io.RandomAccessFile(file, "rw").use { it.setLength(3L * 1024 * 1024 * 1024) }
        try {
            Provisioning.applyAll(context)
            assertTrue(Provisioning.lastError(context)!!.contains("larger than"))
            assertEquals(AppConfig.DEMO_PROFILE_ID, store.config.profiles.single().id)
            // Refused once, not on every check: the fingerprint is stored.
            context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().remove("last_error").commit()
            Provisioning.applyAll(context)
            assertNull(Provisioning.lastError(context))
        } finally {
            file.delete()
        }
    }

    private fun write(json: String, file: File = this.file) {
        file.parentFile!!.mkdirs()
        file.writeText(json)
        // Each write must look new to the change detection (modification time and size).
        file.setLastModified(System.currentTimeMillis() + json.length * 1000L)
    }

    private companion object {
        const val URL = "https://wms.example.com"
    }
}
