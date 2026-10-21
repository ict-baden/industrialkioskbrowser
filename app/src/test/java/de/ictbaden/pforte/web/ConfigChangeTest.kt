package de.ictbaden.pforte.web

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.pforte.config.AppConfig
import de.ictbaden.pforte.config.ConfigStore
import de.ictbaden.pforte.config.PasswordHasher
import de.ictbaden.pforte.provisioning.ConfigReceiver
import de.ictbaden.pforte.provisioning.Provisioning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/** A new configuration reaches a browser session that is in front, without leaving the app first. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigChangeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
        Provisioning.appFolderFile(context)?.delete()
        // Broadcasts without admin password are accepted on managed devices only.
        val owner = ComponentName("com.example.mdm", "com.example.mdm.Admin")
        val dpm = shadowOf(context.getSystemService(DevicePolicyManager::class.java))
        dpm.setActiveAdmin(owner)
        dpm.setDeviceOwner(owner)
    }

    @Test
    fun `an INSTALL_CONFIG broadcast restarts the open session`() {
        val browser = openDemo()
        send(NEW_URL)
        assertRestarted(browser)
    }

    @Test
    fun `a configuration file restarts the open session within a few seconds`() {
        val browser = openDemo()
        Provisioning.appFolderFile(context)!!.writeText(config(NEW_URL))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertRestarted(browser)
    }

    @Test
    fun `a new password keeps the session`() {
        val browser = openDemo()
        ConfigStore.get(context).update { it.copy(global = it.global.copy(exitPasswordHash = PasswordHasher.hash("exit"))) }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(browser.get().isFinishing)
    }

    @Test
    fun `a paused session picks the change up when it comes back`() {
        val browser = openDemo().pause()
        send(NEW_URL)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(browser.get().isFinishing)
        browser.resume()
        assertRestarted(browser)
    }

    @Test
    fun `a paused session does not look at configuration files`() {
        val browser = openDemo().pause()
        Provisioning.appFolderFile(context)!!.writeText(config(NEW_URL))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(AppConfig.DEMO_URL, ConfigStore.get(context).config.profile(AppConfig.DEMO_PROFILE_ID)?.startUrl)
        assertFalse(browser.get().isFinishing)
        browser.resume()
        assertRestarted(browser)
    }

    private fun openDemo(): ActivityController<BrowserActivity> {
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, AppConfig.DEMO_PROFILE_ID)
        return Robolectric.buildActivity(BrowserActivity::class.java, intent).setup()
    }

    private fun send(url: String) {
        val intent = Intent(context.packageName + ConfigReceiver.ACTION_SUFFIX).putExtra(ConfigReceiver.EXTRA_CONFIG, config(url))
        ConfigReceiver().onReceive(context, intent)
    }

    private fun assertRestarted(browser: ActivityController<BrowserActivity>) {
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NEW_URL, ConfigStore.get(context).config.profile(AppConfig.DEMO_PROFILE_ID)?.startUrl)
        assertTrue(browser.get().isFinishing)
        val restarted = shadowOf(browser.get()).nextStartedActivity
        assertEquals(BrowserActivity::class.java.name, restarted.component?.className)
        assertEquals(AppConfig.DEMO_PROFILE_ID, restarted.getStringExtra(BrowserActivity.EXTRA_PROFILE_ID))
        assertNull(shadowOf(browser.get()).nextStartedActivity)
    }

    private fun config(url: String) =
        """{"profiles":[{"id":"${AppConfig.DEMO_PROFILE_ID}","name":"Demo","startUrl":"$url"}]}"""

    private companion object {
        const val NEW_URL = "https://wms.example.com/new"
    }
}
