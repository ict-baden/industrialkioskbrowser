package de.ictbaden.industrialkioskbrowser.web

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

/** The download listener of an open browser session hands only allowed downloads to the DownloadManager. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DownloadTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val downloads get() = shadowOf(context.getSystemService(DownloadManager::class.java))

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a download from a host outside the allow list never reaches the DownloadManager`() {
        offer("https://evil.example.net/tool.apk", profile(allowList = listOf("files.example.com")))
        assertEquals(0, downloads.requestCount)
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue(toast, toast.contains("evil.example.net") && toast.contains(ErrorCodes.DOWNLOAD_NOT_ALLOWED))
    }

    @Test
    fun `a download from an allowed host starts`() {
        offer("https://files.example.com/report.pdf", profile(allowList = listOf("files.example.com")))
        assertEquals(1, downloads.requestCount)
        assertEquals("https://files.example.com/report.pdf", shadowOf(downloads.getRequest(0)).uri.toString())
    }

    @Test
    fun `without an allow list downloads start as before`() {
        offer("https://anything.test/file.zip", profile(allowList = emptyList()))
        assertEquals(1, downloads.requestCount)
    }

    @Test
    fun `allowDownloads off refuses allowed hosts too`() {
        offer("https://files.example.com/report.pdf", profile(allowList = listOf("files.example.com")).copy(allowDownloads = false))
        assertEquals(0, downloads.requestCount)
        assertTrue(ShadowToast.getTextOfLatestToast().contains(ErrorCodes.DOWNLOADS_DISABLED))
    }

    private fun profile(allowList: List<String>) =
        Profile(id = "dl", name = "Downloads", startUrl = "https://wms.example.com/start", allowList = allowList)

    /** Opens the profile and lets the WebView offer [url] as a download, as Chromium does for `<a download>`. */
    private fun offer(url: String, profile: Profile) {
        ConfigStore.get(context).update { it.upsert(profile) }
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id)
        val browser = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().get()
        val webView = browser.findViewById<ViewGroup>(R.id.webContainer).getChildAt(0) as WebView
        shadowOf(webView).downloadListener.onDownloadStart(url, "test-agent", null, "application/octet-stream", 0)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
