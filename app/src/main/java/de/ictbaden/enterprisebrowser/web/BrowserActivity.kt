package de.ictbaden.enterprisebrowser.web

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.security.KeyChain
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import de.ictbaden.enterprisebrowser.BuildConfig
import de.ictbaden.enterprisebrowser.R
import de.ictbaden.enterprisebrowser.admin.AdminActivity
import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.ConfigStore
import de.ictbaden.enterprisebrowser.config.InjectionTiming
import de.ictbaden.enterprisebrowser.config.Profile
import de.ictbaden.enterprisebrowser.config.ViewportMode
import de.ictbaden.enterprisebrowser.config.ScanTerminator
import de.ictbaden.enterprisebrowser.config.ScreenOrientation
import de.ictbaden.enterprisebrowser.scanner.NoScanner
import de.ictbaden.enterprisebrowser.scanner.ScanResult
import de.ictbaden.enterprisebrowser.scanner.ScannerManager
import de.ictbaden.enterprisebrowser.ui.ActionSheet
import de.ictbaden.enterprisebrowser.ui.Brand
import de.ictbaden.enterprisebrowser.ui.Dialogs
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** One browser session for one [Profile]. */
class BrowserActivity : AppCompatActivity(), BridgeHandler.Host {

    private lateinit var profile: Profile
    private lateinit var webView: WebView
    private lateinit var toolbar: View
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var backButton: ImageButton
    private lateinit var scanButton: ImageButton
    private lateinit var keyboardButton: ImageButton
    private lateinit var scanner: ScannerManager
    private lateinit var config: AppConfig
    private lateinit var keyboard: KeyboardController
    private lateinit var sounds: Sounds
    private var brandColor = 0

    private var bridge: BridgeHandler? = null
    private var bridgeAtDocumentStart = false
    private var injectionsAtDocumentStart = false
    private val bridgeScript by lazy { assets.open("js/bridge.js").bufferedReader().use { it.readText() } }
    private val documentStartScript by lazy {
        listOfNotNull(
            InjectionScripts.viewport(profile.viewport),
            InjectionScripts.build(profile.injections, InjectionTiming.DOCUMENT_START, config),
        ).joinToString("\n").ifEmpty { null }
    }
    private val pageLoadedScript by lazy { InjectionScripts.build(profile.injections, InjectionTiming.PAGE_LOADED, config) }
    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain(UrlPolicy.ASSET_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler(ResourcePathHandler.PATH, ResourcePathHandler { config })
            .build()
    }

    private var pagePolicy = PagePolicy.NONE
    private var scannerWanted = true
    private var failedUrl: String? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private val clientCertAliases = ConcurrentHashMap<String, String>()

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileChooserCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileChooserCallback = null
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.let { if (granted) it.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else it.deny() }
        pendingPermission = null
    }

    override val profileId get() = profile.id
    override val scannerName get() = scanner.scanner.name
    override val scannerEnabled get() = scanner.enabled

    override fun setScannerEnabledByPage(enabled: Boolean) {
        scannerWanted = enabled
        updateScannerState()
    }

    override fun softTrigger(start: Boolean?) = scanner.softTrigger(start)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        config = ConfigStore.get(this).config
        profile = config.profile(intent.getStringExtra(EXTRA_PROFILE_ID)) ?: run {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_browser)
        toolbar = findViewById(R.id.toolbar)
        titleView = findViewById(R.id.title)
        subtitleView = findViewById(R.id.subtitle)
        progress = findViewById(R.id.progress)
        backButton = findViewById(R.id.back)
        scanButton = findViewById(R.id.scan)
        keyboardButton = findViewById(R.id.keyboard)
        applyBranding()
        applyInsets()

        scanner = ScannerManager(this, ::onScan)
        sounds = Sounds(this, config)
        scannerWanted = profile.scanner.enabled
        applyKioskSettings()

        webView = WebView(this)
        findViewById<ViewGroup>(R.id.webContainer).addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        keyboard = KeyboardController(this, webView, profile.keyboard, ::perform)
        keyboard.onPageStarted()
        setupToolbar()
        configureWebView()
        onBackPressedDispatcher.addCallback(this) {
            if (!keyboard.onBack()) perform(BrowserAction.BACK)
        }

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            clearDataIfConfigured()
            loadStart()
        }
        updateScannerState()
        updateNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        scanner.start()
        if (profile.kiosk.immersive) hideSystemBars()
    }

    override fun onPause() {
        scanner.stop()
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        bridge?.release()
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && profile.kiosk.immersive) hideSystemBars()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (::keyboard.isInitialized && keyboard.dispatch(event)) || super.dispatchKeyEvent(event)

    /** Browser actions triggered by toolbar, hotkeys, macros and the back key. */
    private fun perform(action: BrowserAction) {
        when (action) {
            BrowserAction.BACK -> if (webView.canGoBack()) webView.goBack() else showMenu()
            BrowserAction.FORWARD -> if (webView.canGoForward()) webView.goForward()
            BrowserAction.STOP -> webView.stopLoading()
            BrowserAction.HOME -> loadStart()
            BrowserAction.RELOAD -> webView.reload()
            BrowserAction.SCAN -> scanner.softTrigger(null)
            BrowserAction.MENU -> showMenu()
            BrowserAction.TOGGLE_KEYBOARD -> keyboard.toggleSoftKeyboard()
        }
    }

    // region setup

    private fun applyKioskSettings() {
        requestedOrientation = when (profile.kiosk.orientation) {
            ScreenOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        if (profile.kiosk.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toolbar.isVisible = !profile.kiosk.hideToolbar
    }

    /** Profile/brand color behind toolbar and status bar; title shows the profile until a page loads. */
    private fun applyBranding() {
        brandColor = Brand.color(this, config, profile)
        val onColor = Brand.onColor(brandColor)
        findViewById<View>(R.id.root).setBackgroundColor(brandColor)
        Brand.applySystemBars(this, brandColor)
        titleView.setTextColor(onColor)
        subtitleView.setTextColor(Brand.onColorSecondary(brandColor))
        listOf<ImageButton>(backButton, scanButton, keyboardButton, findViewById(R.id.menu)).forEach {
            it.imageTintList = ColorStateList.valueOf(onColor)
        }
        progress.setIndicatorColor(if (Brand.isLight(brandColor)) Brand.darker(brandColor, 0.4f) else brandColor)
        titleView.text = profile.name
        showAddress(profile.startUrl)
    }

    /** Toolbar extends under the status bar; content stays clear of navigation bar and keyboard. */
    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            toolbar.updatePadding(top = bars.top)
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                top = if (toolbar.isVisible) 0 else bars.top,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            insets
        }
    }

    private fun showAddress(url: String) {
        val secure = url.startsWith("https://", ignoreCase = true)
        subtitleView.text = Brand.address(url)
        val lock = if (secure) ContextCompat.getDrawable(this, R.drawable.ic_lock)?.mutate() else null
        lock?.let {
            val size = (12 * resources.displayMetrics.density).toInt()
            it.setBounds(0, 0, size, size)
            it.setTint(Brand.onColorSecondary(brandColor))
        }
        subtitleView.setCompoundDrawablesRelative(lock, null, null, null)
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupToolbar() {
        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<View>(R.id.menu).setOnClickListener { showMenu() }
        scanButton.isVisible = scanner.scanner !is NoScanner
        scanButton.setOnClickListener { scanner.softTrigger(null) }
        keyboardButton.isVisible = keyboard.softKeyboardAvailable && !profile.keyboard.showOnFocus
        keyboardButton.setOnClickListener { keyboard.toggleSoftKeyboard() }
    }

    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            val viewport = profile.viewport
            // DEVICE ignores the page's viewport width and lays out at screen width.
            useWideViewPort = viewport.mode != ViewportMode.DEVICE
            loadWithOverviewMode = viewport.fitToScreen
            setSupportZoom(viewport.pinchZoom)
            builtInZoomControls = viewport.pinchZoom
            displayZoomControls = false
            textZoom = viewport.textZoom
            profile.userAgent?.let { userAgentString = it.replace("{default}", WebSettings.getDefaultUserAgent(this@BrowserActivity)) }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        if (profile.viewport.initialScale > 0) webView.setInitialScale(profile.viewport.initialScale)
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            download(url, userAgent, contentDisposition, mimeType)
        }
        installBridge()
        installDocumentStartScript()
    }

    /** Viewport override and document-start injections; they check the URL themselves, so they match all origins. */
    @SuppressLint("RequiresFeature") // guarded by isSupported()
    private fun installDocumentStartScript() {
        val script = documentStartScript ?: return
        if (isSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            injectionsAtDocumentStart = true
        } else {
            Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported, injecting after page load")
        }
    }

    @SuppressLint("RequiresFeature") // guarded by isSupported()
    private fun installBridge() {
        val rules = UrlPolicy.bridgeOriginRules(profile)
        val handler = BridgeHandler(this, this)
        if (isSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try {
                WebViewCompat.addWebMessageListener(webView, BridgeHandler.JS_OBJECT_NAME, rules, handler)
                bridge = handler
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid bridge origin rules $rules", e)
            }
        }
        if (bridge == null) {
            Log.w(TAG, "JS bridge disabled")
            handler.release()
            return
        }
        if (isSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeScript, rules)
            bridgeAtDocumentStart = true
        }
    }

    private fun isSupported(feature: String) =
        runCatching { WebViewFeature.isFeatureSupported(feature) }.getOrDefault(false)

    private fun clearDataIfConfigured() {
        if (profile.clearCacheOnStart) {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
        }
        if (profile.clearCookiesOnStart) CookieManager.getInstance().removeAllCookies(null)
    }

    // endregion

    private fun loadStart() {
        failedUrl = null
        webView.loadUrl(profile.startUrl)
    }

    private fun updateScannerState() {
        scanner.enabled = scannerWanted
        scanButton.alpha = if (scannerWanted) 1f else 0.4f
    }

    private fun updateNavigation() {
        backButton.isEnabled = webView.canGoBack()
        backButton.alpha = if (backButton.isEnabled) 1f else 0.4f
    }

    // region scanning

    private fun onScan(scan: ScanResult) {
        val template = pagePolicy.navigateTemplate
        if (template != null) {
            val filled = ScanScripts.applyTemplate(template, scan.data)
            // Relative targets ("/item?code=%s") are resolved against the current page.
            val target = if (filled.startsWith("javascript:", ignoreCase = true)) filled
            else runCatching { java.net.URI(webView.url).resolve(filled).toString() }.getOrDefault(filled)
            when {
                target.startsWith("javascript:", ignoreCase = true) ->
                    webView.evaluateJavascript(target.substringAfter(':'), null)
                UrlPolicy.isAllowed(target, profile) -> webView.loadUrl(target)
                else -> toast("Target not allowed: $target")
            }
            return
        }
        val terminator = pagePolicy.terminator ?: profile.scanner.terminator
        webView.requestFocus()
        webView.evaluateJavascript(ScanScripts.deliver(scan, profile.scanner.injectIntoFocusedField)) { result ->
            sounds.play(profile.sounds.scanOk)
            if (ScanScripts.decodeStringResult(result) == "inserted") sendTerminator(terminator)
        }
    }

    private fun sendTerminator(terminator: ScanTerminator) {
        val keys = when (terminator) {
            ScanTerminator.NONE -> emptyList()
            ScanTerminator.TAB -> listOf(KeyEvent.KEYCODE_TAB)
            ScanTerminator.ENTER -> listOf(KeyEvent.KEYCODE_ENTER)
            ScanTerminator.TAB_ENTER -> listOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER)
            ScanTerminator.ENTER_TAB -> listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB)
        }
        keys.forEachIndexed { index, code ->
            webView.postDelayed({ keyboard.pressKey(code) }, KEY_DELAY_MS * (index + 1))
        }
    }

    // endregion

    // region menu & exit

    @VisibleForTesting
    internal fun showMenu() {
        val config = ConfigStore.get(this).config
        val actions = buildList {
            add(ActionSheet.Action("Home", R.drawable.ic_home) { loadStart() })
            add(ActionSheet.Action("Reload", R.drawable.ic_refresh) { webView.reload() })
            add(ActionSheet.Action("Forward", R.drawable.ic_forward, enabled = webView.canGoForward()) { webView.goForward() })
            add(ActionSheet.Action("Scanner", R.drawable.ic_scan, active = scannerWanted) { setScannerEnabledByPage(!scannerWanted) })
            if (keyboard.softKeyboardAvailable) {
                add(ActionSheet.Action("Keyboard", R.drawable.ic_keyboard) { keyboard.toggleSoftKeyboard() })
            }
            if (config.profiles.size > 1 && !config.global.hideExit) {
                add(ActionSheet.Action("Profiles", R.drawable.ic_swap) { finish() })
            }
            add(ActionSheet.Action("Settings", R.drawable.ic_settings) {
                Dialogs.requireAdmin(this@BrowserActivity) { startActivity(Intent(this@BrowserActivity, AdminActivity::class.java)) }
            })
            if (!config.global.hideExit) {
                add(ActionSheet.Action("Exit", R.drawable.ic_power) {
                    Dialogs.requirePassword(this@BrowserActivity, config.global.exitPasswordHash, "Exit password", ::exitApp)
                })
            }
        }
        ActionSheet(this).show(profile.name, subtitleView.text.toString(), brandColor, actions)
    }

    private fun exitApp() {
        val activityManager = getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { stopLockTask() }
        }
        finishAffinity()
    }

    // endregion

    private fun showStatus(title: String, message: String, url: String?) {
        failedUrl = url
        sounds.play(profile.sounds.error)
        val html = StatusPage.render(this, config.content(profile.errorPages.template), title, message, url, "#%06X".format(0xFFFFFF and brandColor))
        webView.loadDataWithBaseURL(StatusPage.BASE_URL, html, "text/html", "utf-8", null)
    }

    private fun download(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!profile.allowDownloads) {
            toast("Downloads are disabled")
            return
        }
        if (!URLUtil.isNetworkUrl(url)) {
            toast("This download type is not supported")
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(url.toUri())
            .setTitle(fileName)
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        getSystemService(DownloadManager::class.java).enqueue(request)
        toast("Download started: $fileName")
    }

    private fun chooseClientCertificate(request: ClientCertRequest) {
        val key = "${request.host}:${request.port}"
        clientCertAliases[key]?.let {
            proceedWithAlias(request, it)
            return
        }
        KeyChain.choosePrivateKeyAlias(
            this,
            { alias ->
                if (alias == null) {
                    request.cancel()
                } else {
                    clientCertAliases[key] = alias
                    proceedWithAlias(request, alias)
                }
            },
            request.keyTypes,
            request.principals,
            request.host,
            request.port,
            null,
        )
    }

    private fun proceedWithAlias(request: ClientCertRequest, alias: String) = thread(name = "client-cert") {
        try {
            val key = KeyChain.getPrivateKey(this, alias)
            val chain = KeyChain.getCertificateChain(this, alias)
            if (key != null && chain != null) request.proceed(key, chain) else request.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Client certificate $alias unavailable", e)
            request.cancel()
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private inner class Client : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // shouldOverrideUrlLoading is not called for POST (form submit): stop those here, before they
            // reach the server. A 204 response makes the WebView stay on the current page.
            val web = request.url.scheme == "http" || request.url.scheme == "https" // not the data: URL of the error page
            if (request.isForMainFrame && web && !UrlPolicy.isAllowed(request.url.toString(), profile)) {
                view.post { blocked(request.url) }
                return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), null)
            }
            return assetLoader.shouldInterceptRequest(request.url)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url == StatusPage.RETRY_URL) {
                failedUrl?.let(view::loadUrl) ?: view.reload()
                return true
            }
            if (!request.isForMainFrame || UrlPolicy.isAllowed(url, profile)) return false
            blocked(request.url)
            return true
        }

        private fun blocked(url: Uri) {
            toast("Page not allowed: ${url.host ?: url}")
            sounds.play(profile.sounds.error)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pagePolicy = PagePolicy.NONE
            scannerWanted = profile.scanner.enabled
            updateScannerState()
            keyboard.onPageStarted()
            progress.isVisible = true
            if (!url.startsWith(StatusPage.BASE_URL)) showAddress(url)
            updateNavigation()
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress.isVisible = false
            titleView.text = view.title?.takeIf { it.isNotBlank() && !it.startsWith("data:") } ?: url
            updateNavigation()
            if (bridge != null && !bridgeAtDocumentStart && UrlPolicy.isBridgeOrigin(url, profile)) {
                view.evaluateJavascript(bridgeScript, null)
            }
            if (!url.startsWith(StatusPage.BASE_URL)) {
                if (!injectionsAtDocumentStart) documentStartScript?.let { view.evaluateJavascript(it, null) }
                pageLoadedScript?.let { view.evaluateJavascript(it, null) }
            }
            keyboard.onPageFinished()
            view.evaluateJavascript(ScanScripts.COLLECT_META_TAGS) { result ->
                pagePolicy = PagePolicy.fromMetaTags(ScanScripts.parseMetaTags(result))
                pagePolicy.scannerEnabled?.let { scannerWanted = it }
                updateScannerState()
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            // A navigation stopped in shouldInterceptRequest (not allowed) stays on the current page.
            if (!UrlPolicy.isAllowed(request.url.toString(), profile)) return
            val (title, message) = when (error.errorCode) {
                ERROR_HOST_LOOKUP -> "Server not found" to "The address could not be resolved. Check the Wi-Fi connection."
                ERROR_CONNECT -> "No connection" to "The server cannot be reached."
                ERROR_TIMEOUT -> "Timeout" to "The server did not respond in time."
                ERROR_FAILED_SSL_HANDSHAKE -> "Insecure connection" to "The secure connection could not be established."
                else -> "Page not available" to "The page could not be loaded."
            }
            val detail = error.description?.toString()?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            showStatus(title, message + detail, request.url.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!profile.errorPages.showHttpErrors || !request.isForMainFrame || errorResponse.statusCode < 400) return
            showStatus(
                "Error ${errorResponse.statusCode}",
                errorResponse.reasonPhrase?.takeIf { it.isNotBlank() } ?: "The server rejected the request.",
                request.url.toString(),
            )
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            val reason = when (error.primaryError) {
                SslError.SSL_UNTRUSTED -> "The certificate is not issued by a trusted authority."
                SslError.SSL_EXPIRED -> "The certificate has expired."
                SslError.SSL_NOTYETVALID -> "The certificate is not yet valid."
                SslError.SSL_IDMISMATCH -> "The certificate does not match the server name."
                SslError.SSL_DATE_INVALID -> "The certificate date is invalid."
                else -> "The certificate is invalid."
            }
            showStatus("Insecure connection", "$reason Trusted certificates are installed as a CA through the MDM.", error.url)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String?) {
            Dialogs.credentials(this@BrowserActivity, host) { credentials ->
                if (credentials == null) handler.cancel() else handler.proceed(credentials.first, credentials.second)
            }
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) =
            chooseClientCertificate(request)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.e(TAG, "WebView renderer gone (crash=${detail.didCrash()}), restarting session")
            startActivity(intent)
            finish()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.setProgressCompat(newProgress, true)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) titleView.text = title
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            if (!profile.allowFileUpload) return false
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            return try {
                fileChooser.launch(fileChooserParams.createIntent())
                true
            } catch (_: ActivityNotFoundException) {
                fileChooserCallback = null
                false
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val wantsCamera = PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources
            if (!wantsCamera || !UrlPolicy.isAllowed(request.origin.toString(), profile)) {
                request.deny()
                return
            }
            if (ContextCompat.checkSelfPermission(this@BrowserActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                pendingPermission?.deny()
                pendingPermission = request
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val TAG = "BrowserActivity"
        private const val KEY_DELAY_MS = 50L
    }
}
