package io.github.pieewiee.enterprisebrowser.web

import android.Manifest
import android.app.ActivityManager
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.pieewiee.enterprisebrowser.BuildConfig
import io.github.pieewiee.enterprisebrowser.R
import io.github.pieewiee.enterprisebrowser.admin.AdminActivity
import io.github.pieewiee.enterprisebrowser.config.AppConfig
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.InjectionTiming
import io.github.pieewiee.enterprisebrowser.config.Profile
import io.github.pieewiee.enterprisebrowser.config.ViewportMode
import io.github.pieewiee.enterprisebrowser.config.ScanTerminator
import io.github.pieewiee.enterprisebrowser.config.ScreenOrientation
import io.github.pieewiee.enterprisebrowser.scanner.NoScanner
import io.github.pieewiee.enterprisebrowser.scanner.ScanResult
import io.github.pieewiee.enterprisebrowser.scanner.ScannerManager
import io.github.pieewiee.enterprisebrowser.ui.Dialogs
import io.github.pieewiee.enterprisebrowser.ui.applySystemInsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** One browser session for one [Profile]. */
class BrowserActivity : AppCompatActivity(), BridgeHandler.Host {

    private lateinit var profile: Profile
    private lateinit var webView: WebView
    private lateinit var toolbar: View
    private lateinit var titleView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var scanButton: ImageButton
    private lateinit var keyboardButton: ImageButton
    private lateinit var scanner: ScannerManager
    private lateinit var config: AppConfig
    private lateinit var keyboard: KeyboardController
    private lateinit var sounds: Sounds

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
        config = ConfigStore.get(this).config
        profile = config.profile(intent.getStringExtra(EXTRA_PROFILE_ID)) ?: run {
            Toast.makeText(this, "Profil nicht gefunden", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_browser)
        applySystemInsets(findViewById(R.id.root))
        toolbar = findViewById(R.id.toolbar)
        titleView = findViewById(R.id.title)
        progress = findViewById(R.id.progress)
        backButton = findViewById(R.id.back)
        forwardButton = findViewById(R.id.forward)
        scanButton = findViewById(R.id.scan)
        keyboardButton = findViewById(R.id.keyboard)

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

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setupToolbar() {
        backButton.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        forwardButton.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<View>(R.id.home).setOnClickListener { loadStart() }
        findViewById<View>(R.id.reload).setOnClickListener { webView.reload() }
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
    private fun installDocumentStartScript() {
        val script = documentStartScript ?: return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            injectionsAtDocumentStart = true
        } else {
            Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported, injecting after page load")
        }
    }

    private fun installBridge() {
        val rules = UrlPolicy.bridgeOriginRules(profile)
        val handler = BridgeHandler(this, this)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
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
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeScript, rules)
            bridgeAtDocumentStart = true
        }
    }

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
        forwardButton.isEnabled = webView.canGoForward()
        forwardButton.alpha = if (forwardButton.isEnabled) 1f else 0.4f
    }

    // region scanning

    private fun onScan(scan: ScanResult) {
        val template = pagePolicy.navigateTemplate
        if (template != null) {
            val target = ScanScripts.applyTemplate(template, scan.data)
            when {
                target.startsWith("javascript:", ignoreCase = true) ->
                    webView.evaluateJavascript(target.substringAfter(':'), null)
                UrlPolicy.isAllowed(target, profile) -> webView.loadUrl(target)
                else -> toast("Ziel nicht erlaubt: $target")
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

    private fun showMenu() {
        val config = ConfigStore.get(this).config
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += (if (scannerWanted) "Scanner deaktivieren" else "Scanner aktivieren") to {
            setScannerEnabledByPage(!scannerWanted)
        }
        if (profile.kiosk.hideToolbar) {
            actions += "Startseite" to ::loadStart
            actions += "Neu laden" to { webView.reload() }
        }
        if (config.profiles.size > 1 && !config.global.hideExit) actions += "Profil wechseln" to ::finish
        actions += "Einstellungen" to { Dialogs.requireAdmin(this) { startActivity(Intent(this, AdminActivity::class.java)) } }
        if (!config.global.hideExit) {
            actions += "Beenden" to {
                Dialogs.requirePassword(this, config.global.exitPasswordHash, "Exit-Passwort", ::exitApp)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
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
        val html = StatusPage.render(this, config.content(profile.errorPages.template), title, message, url)
        webView.loadDataWithBaseURL(StatusPage.BASE_URL, html, "text/html", "utf-8", null)
    }

    private fun download(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!profile.allowDownloads) {
            toast("Downloads sind deaktiviert")
            return
        }
        if (!URLUtil.isNetworkUrl(url)) {
            toast("Dieser Download-Typ wird nicht unterstützt")
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
        toast("Download gestartet: $fileName")
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

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url == StatusPage.RETRY_URL) {
                failedUrl?.let(view::loadUrl) ?: view.reload()
                return true
            }
            if (!request.isForMainFrame || UrlPolicy.isAllowed(url, profile)) return false
            toast("Seite nicht erlaubt: ${request.url.host ?: url}")
            sounds.play(profile.sounds.error)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            pagePolicy = PagePolicy.NONE
            scannerWanted = profile.scanner.enabled
            updateScannerState()
            keyboard.onPageStarted()
            progress.isVisible = true
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
            showStatus("Seite nicht erreichbar", error.description?.toString().orEmpty(), request.url.toString())
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!profile.errorPages.showHttpErrors || !request.isForMainFrame || errorResponse.statusCode < 400) return
            showStatus(
                "Fehler ${errorResponse.statusCode}",
                errorResponse.reasonPhrase?.takeIf { it.isNotBlank() } ?: "Der Server hat die Anfrage abgelehnt.",
                request.url.toString(),
            )
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            val reason = when (error.primaryError) {
                SslError.SSL_UNTRUSTED -> "Das Zertifikat stammt nicht von einer vertrauenswürdigen Stelle."
                SslError.SSL_EXPIRED -> "Das Zertifikat ist abgelaufen."
                SslError.SSL_NOTYETVALID -> "Das Zertifikat ist noch nicht gültig."
                SslError.SSL_IDMISMATCH -> "Das Zertifikat passt nicht zum Servernamen."
                SslError.SSL_DATE_INVALID -> "Das Zertifikatsdatum ist ungültig."
                else -> "Das Zertifikat ist ungültig."
            }
            showStatus("Unsichere Verbindung", "$reason Vertrauenswürdige Zertifikate werden über das MDM als CA installiert.", error.url)
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
            progress.progress = newProgress
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
