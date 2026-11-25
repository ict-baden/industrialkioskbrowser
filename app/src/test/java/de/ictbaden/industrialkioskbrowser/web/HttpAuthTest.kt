package de.ictbaden.industrialkioskbrowser.web

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.Profile
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** The sign-in dialog only appears for hosts the profile may load. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HttpAuthTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowDialog.reset()
    }

    @Test
    fun `an embedded resource of a foreign host gets no sign-in dialog`() {
        ask("httpbin.org", allowList = listOf("sso.example.org"))
        assertNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `an allowed host gets the sign-in dialog`() {
        ask("sso.example.org", allowList = listOf("sso.example.org"))
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    @Test
    fun `without an allow list the dialog appears as before`() {
        ask("httpbin.org", allowList = emptyList())
        assertNotNull(ShadowDialog.getLatestDialog())
    }

    private fun ask(host: String, allowList: List<String>) {
        val profile = Profile(id = "auth", name = "Auth", startUrl = "https://wms.example.com/start", allowList = allowList)
        ConfigStore.get(context).update { it.upsert(profile) }
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id)
        val browser = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().get()
        val webView = browser.findViewById<ViewGroup>(R.id.webContainer).getChildAt(0) as WebView
        val handler = ReflectionHelpers.callConstructor(HttpAuthHandler::class.java)
        shadowOf(webView).webViewClient.onReceivedHttpAuthRequest(webView, handler, host, "WMS Anmeldung")
        shadowOf(Looper.getMainLooper()).idle()
    }
}
