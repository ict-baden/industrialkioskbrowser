package de.ictbaden.industrialkioskbrowser.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Looper
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.Date

/** A certificate error of an embedded resource leaves the page alone; one of the page shows the error page. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CertificateErrorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var webView: WebView

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
        val profile = Profile(id = "tls", name = "TLS", startUrl = PAGE)
        ConfigStore.get(context).update { it.upsert(profile) }
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id)
        val browser = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().get()
        webView = browser.findViewById<ViewGroup>(R.id.webContainer).getChildAt(0) as WebView
        // What the WebView does before it connects: it asks for the main-frame request.
        client.shouldInterceptRequest(webView, request(PAGE, mainFrame = true))
    }

    @Test
    fun `an image with a bad certificate does not replace the page`() {
        client.shouldInterceptRequest(webView, request("https://self-signed.badssl.com/pixel.png", mainFrame = false))
        sslError("https://self-signed.badssl.com/pixel.png")
        assertNull(shadowOf(webView).lastLoadDataWithBaseURL)
    }

    @Test
    fun `a bad certificate of the page shows the error page`() {
        sslError(PAGE)
        val shown = shadowOf(webView).lastLoadDataWithBaseURL
        assertEquals(StatusPage.BASE_URL, shown.baseUrl)
        assertTrue(shown.data.contains(ErrorCodes.CERTIFICATE))
    }

    @Test
    fun `a redirect to a host with a bad certificate shows the error page`() {
        // Redirects only pass shouldOverrideUrlLoading.
        client.shouldOverrideUrlLoading(webView, request("https://sso.example.org/login", mainFrame = true))
        sslError("https://sso.example.org/login")
        assertEquals(StatusPage.BASE_URL, shadowOf(webView).lastLoadDataWithBaseURL.baseUrl)
    }

    private val client get() = shadowOf(webView).webViewClient

    private fun sslError(url: String) {
        @Suppress("DEPRECATION")
        val certificate = SslCertificate("CN=test", "CN=test", Date(0), Date(1))
        val handler = ReflectionHelpers.callConstructor(SslErrorHandler::class.java)
        client.onReceivedSslError(webView, handler, SslError(SslError.SSL_EXPIRED, certificate, url))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun request(url: String, mainFrame: Boolean) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private companion object {
        const val PAGE = "https://wms.example.com/app"
    }
}
