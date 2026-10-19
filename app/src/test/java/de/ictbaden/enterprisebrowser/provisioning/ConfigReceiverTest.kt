package de.ictbaden.enterprisebrowser.provisioning

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.ConfigStore
import de.ictbaden.enterprisebrowser.config.GlobalSettings
import de.ictbaden.enterprisebrowser.config.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigReceiverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ConfigStore.get(context)

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
    }

    @Test
    fun `refuses a foreign configuration on an unmanaged device without password`() {
        send()
        assertNotEquals(EVIL_URL, store.config.profiles.first().startUrl)
        assertEquals(true, Provisioning.lastError(context)?.contains("not managed"))
    }

    @Test
    fun `accepts the broadcast on a device with a device owner`() {
        val owner = ComponentName("com.example.mdm", "com.example.mdm.Admin")
        val dpm = shadowOf(context.getSystemService(DevicePolicyManager::class.java))
        dpm.setActiveAdmin(owner)
        dpm.setDeviceOwner(owner)
        send()
        assertEquals(EVIL_URL, store.config.profiles.single().startUrl)
    }

    @Test
    fun `accepts the broadcast with the admin password only`() {
        store.import(AppConfig.initial("Demo").copy(global = GlobalSettings(adminPasswordHash = PasswordHasher.hash("secret"))))
        send()
        send(password = "wrong")
        assertNotEquals(EVIL_URL, store.config.profiles.first().startUrl)
        send(password = "secret")
        assertEquals(EVIL_URL, store.config.profiles.single().startUrl)
    }

    private fun send(password: String? = null) {
        val intent = Intent(context.packageName + ConfigReceiver.ACTION_SUFFIX)
            .putExtra(ConfigReceiver.EXTRA_CONFIG, """{"profiles":[{"startUrl":"$EVIL_URL"}]}""")
        password?.let { intent.putExtra(ConfigReceiver.EXTRA_ADMIN_PASSWORD, it) }
        ConfigReceiver().onReceive(context, intent)
    }

    private companion object {
        const val EVIL_URL = "https://phishing.example"
    }
}
