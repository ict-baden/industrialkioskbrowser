package de.ictbaden.industrialkioskbrowser.web

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.scanner.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.Duration

/**
 * The queue inside [BrowserActivity]: page events and JavaScript answers are played by hand,
 * because Robolectric's WebView runs no page. The state logic itself is in [ScanQueueTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanQueueBrowserTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
    }

    @Test
    fun `a scan while the first page loads is delivered once it has loaded`() {
        val browser = openDemo()
        val activity = browser.get()
        activity.onScan(scan("SCAN-1"))
        assertEquals(1, activity.scansPending)
        assertFalse(activity.scanReady)
        assertFalse(lastScript().contains("SCAN-1"))

        pageLoaded()
        idle(199)
        assertFalse(lastScript().contains("SCAN-1"))
        idle(1)
        assertTrue(lastScript().contains("\"data\":\"SCAN-1\""))
        assertEquals(0, activity.scansPending)
    }

    @Test
    fun `after Enter the next scan waits for the grace time`() {
        val browser = openDemo()
        val activity = browser.get()
        pageLoaded()
        idle(200)
        assertTrue(activity.scanReady)

        activity.onScan(scan("A"))
        assertTrue(lastScript().contains("\"data\":\"A\""))
        answer("\"inserted\"")
        activity.onScan(scan("B"))
        assertEquals(1, activity.scansPending)
        idle(50 + 399) // Enter after 50 ms, then 400 ms for the form to navigate
        assertFalse(lastScript().contains("\"data\":\"B\""))
        idle(1)
        assertTrue(lastScript().contains("\"data\":\"B\""))
    }

    @Test
    fun `busy from the page holds scans back until ready`() {
        val browser = openDemo()
        val activity = browser.get()
        pageLoaded()
        idle(200)
        activity.pageBusy(null)
        activity.onScan(scan("A"))
        idle(5_000)
        assertFalse(lastScript().contains("\"data\":\"A\""))
        activity.pageReady()
        assertTrue(lastScript().contains("\"data\":\"A\""))
    }

    @Test
    fun `scans still waiting when the session closes are reported`() {
        val browser = openDemo()
        val activity = browser.get()
        activity.onScan(scan("A"))
        activity.onScan(scan("B"))
        browser.pause().stop().destroy()
        val expected = context.resources.getQuantityString(R.plurals.scan_discarded_multiple, 2, 2, context.getString(R.string.scan_drop_closed))
        assertEquals(context.getString(R.string.message_with_code, expected, ErrorCodes.SCAN_QUEUE_CLOSED), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a scan that waits too long is discarded with a message`() {
        val browser = openDemo()
        val activity = browser.get()
        // The server takes longer than scanner.queue.timeoutMs to finish the page.
        shadowOf(webView).webViewClient.onPageStarted(webView, AppConfig.DEMO_URL, null)
        activity.onScan(scan("LATE"))
        idle(14_999)
        assertEquals(null, ShadowToast.getTextOfLatestToast())
        idle(1)
        val expected = context.getString(R.string.scan_discarded_single, "LATE", context.getString(R.string.scan_drop_timeout))
        assertEquals(context.getString(R.string.message_with_code, expected, ErrorCodes.SCAN_QUEUE_TIMEOUT), ShadowToast.getTextOfLatestToast())
        assertEquals(0, activity.scansPending)
    }

    @Test
    fun `without the queue a scan goes to the page at once, as before`() {
        ConfigStore.get(context).update { config ->
            config.copy(profiles = config.profiles.map { it.copy(scanner = it.scanner.copy(queue = it.scanner.queue.copy(enabled = false))) })
        }
        val browser = openDemo()
        browser.get().onScan(scan("NOW"))
        assertTrue(lastScript().contains("\"data\":\"NOW\""))
    }

    private lateinit var webView: WebView

    private fun openDemo(): ActivityController<BrowserActivity> {
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, AppConfig.DEMO_PROFILE_ID)
        return Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().also {
            webView = it.get().findViewById<ViewGroup>(R.id.webContainer).getChildAt(0) as WebView
        }
    }

    /** onPageStarted, onPageFinished and the answer of the meta-tag script, as a WebView would send them. */
    private fun pageLoaded() {
        val client = shadowOf(webView).webViewClient
        client.onPageStarted(webView, AppConfig.DEMO_URL, null)
        client.onPageFinished(webView, AppConfig.DEMO_URL)
        assertTrue(lastScript().contains("scannernavigate"))
        answer("\"[]\"")
    }

    private fun lastScript(): String = shadowOf(webView).lastEvaluatedJavascript ?: ""

    private fun answer(value: String) {
        shadowOf(webView).lastEvaluatedJavascriptCallback!!.onReceiveValue(value)
    }

    private fun scan(data: String) = ScanResult(data, "CODE128", "test", 0)

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
}
