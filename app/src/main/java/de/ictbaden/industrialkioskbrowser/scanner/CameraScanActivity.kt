package de.ictbaden.industrialkioskbrowser.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.AimMode
import de.ictbaden.industrialkioskbrowser.config.CameraSettings
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.Gs1Mode
import de.ictbaden.industrialkioskbrowser.config.ScanDataSettings
import de.ictbaden.industrialkioskbrowser.web.ErrorCodes
import zxingcpp.BarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen camera scanner for devices without a scan engine (issue #48), with the settings of
 * `scanner.camera` of the profile: aiming frame or crosshair, barcode types, length, confirmation
 * over several frames, zoom, torch, beep and timeout.
 *
 * Single scan: the first code ends the screen with the result. Continuous mode: every new code goes
 * to [CameraScanBus] at once and the screen stays open until ✕ or the timeout. Not exported; it runs
 * in the task of the browser, so it also works in lock task (kiosk) mode.
 */
class CameraScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var viewfinder: ViewfinderView
    private lateinit var torchButton: ImageButton
    private lateinit var zoomButton: TextView
    private lateinit var status: TextView
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var settings: CameraSettings
    private lateinit var data: ScanDataSettings
    private var configImport = false
    private val reader by lazy { BarcodeReader(readerOptions()) }
    private val done = AtomicBoolean(false)
    private var camera: Camera? = null
    private var torchOn = false
    private var zoomStep = 0
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish() }
    private var tone: ToneGenerator? = null

    /** Size of the preview for the aiming geometry; written on the main thread, read by the analyser. */
    @Volatile
    private var viewSize = 0 to 0

    /** Confirmation: the text seen in the last frames and how often in a row. Analyser thread only. */
    private var candidate: String? = null
    private var candidateCount = 0

    /** Continuous mode: when each text was last accepted. Analyser thread only. */
    private val lastAccepted = HashMap<String, Long>()
    private var accepted = 0

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else fail(R.string.camera_permission_denied, ErrorCodes.CAMERA_PERMISSION_DENIED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = intent.getIntExtra(EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        configImport = intent.getBooleanExtra(EXTRA_CONFIG_IMPORT, false)
        val profile = intent.getStringExtra(EXTRA_PROFILE)?.let { ConfigStore.get(this).config.profile(it) }
        // A configuration barcode is read with the plain defaults: anywhere in the picture, any type.
        settings = if (configImport) CameraSettings(aim = AimMode.ANYWHERE) else profile?.scanner?.camera ?: CameraSettings()
        data = if (configImport) ScanDataSettings(trim = false) else profile?.scanner?.data ?: ScanDataSettings()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        analysisExecutor = Executors.newSingleThreadExecutor()
        if (settings.beep) tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME) }.getOrNull()
        setContentView(buildLayout())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
        restartTimeout()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdown()
        tone?.release()
        super.onDestroy()
    }

    private fun readerOptions() = BarcodeReader.Options(
        formats = Symbologies.toZxing(settings.symbologies).mapNotNull { name ->
            runCatching { BarcodeReader.Format.valueOf(name) }.getOrNull()
        }.toSet(),
        tryRotate = true,
        tryInvert = settings.inverted,
        tryHarder = false,
        textMode = if (data.gs1 == Gs1Mode.RAW) BarcodeReader.TextMode.PLAIN else BarcodeReader.TextMode.HRI,
    )

    private fun buildLayout(): View {
        // PreviewView turns and centre-crops the picture itself, without distortion.
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> viewSize = v.width to v.height }
        }
        viewfinder = ViewfinderView(this, settings.aim)
        val close = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            contentDescription = getString(R.string.camera_close)
            setBackgroundResource(R.drawable.bg_camera_button)
            setOnClickListener { finish() }
        }
        torchButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_flashlight)
            contentDescription = getString(R.string.camera_torch)
            setBackgroundResource(R.drawable.bg_camera_button)
            isVisible = false
            setOnClickListener { setTorch(!torchOn) }
        }
        zoomButton = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setBackgroundResource(R.drawable.bg_camera_button)
            contentDescription = getString(R.string.camera_zoom)
            isVisible = false
            setOnClickListener { cycleZoom() }
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(zoomButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(torchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        val hint = TextView(this).apply {
            text = getString(if (settings.aim == AimMode.CENTER) R.string.camera_hint_center else R.string.camera_hint)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        // Continuous mode: how many codes were entered and the last one.
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            isVisible = false
            maxLines = 2
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(32))
            addView(status)
            addView(hint)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(viewfinder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(12), left = bars.left + dp(12), right = bars.right + dp(12))
            bottom.updatePadding(bottom = bars.bottom + dp(32))
            insets
        }
        return root
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider unavailable", e)
                fail(R.string.camera_unavailable, ErrorCodes.CAMERA_UNAVAILABLE)
                return@addListener
            }
            bind(provider)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bind(provider: ProcessCameraProvider) {
        if (isFinishing || isDestroyed) return
        // A configuration barcode can be a dense QR code (version 40): read it at a higher resolution.
        val wanted = if (configImport) Size(1920, 1080) else Size(1280, 720)
        val resolution = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(wanted, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build()
        preview.surfaceProvider = previewView.surfaceProvider
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            try {
                if (!done.get()) analyse(image)
            } catch (e: Exception) {
                Log.w(TAG, "Decoding failed", e)
            } finally {
                image.close()
            }
        }
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> null
        }
        if (selector == null) {
            fail(R.string.camera_unavailable, ErrorCodes.CAMERA_UNAVAILABLE)
            return
        }
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, analysis).also { cam ->
                torchButton.isVisible = cam.cameraInfo.hasFlashUnit()
                zoomButton.isVisible = (cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f) > 1.01f
                if (settings.torch) setTorch(true)
                setZoom(settings.zoom.toFloat())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Binding the camera failed", e)
            fail(R.string.camera_unavailable, ErrorCodes.CAMERA_UNAVAILABLE)
        }
    }

    /** Analyser thread: picks the code to take from one frame and decides whether it counts. */
    private fun analyse(image: ImageProxy) {
        val results = reader.read(image).filter { result ->
            val text = result.text
            !text.isNullOrEmpty() && result.error == null &&
                (settings.minLength == 0 || text.length >= settings.minLength) &&
                (settings.maxLength == 0 || text.length <= settings.maxLength) &&
                // Codes the rules drop are ignored here, so the camera keeps looking.
                (configImport || ScanFormatter.format(text, Symbologies.fromZxing(result.format.name), data) != null)
        }
        val (viewW, viewH) = viewSize
        val turned = image.imageInfo.rotationDegrees % 180 != 0
        val imageW = if (turned) image.cropRect.height() else image.cropRect.width()
        val imageH = if (turned) image.cropRect.width() else image.cropRect.height()
        val index = if (viewW == 0 || viewH == 0) {
            results.indices.firstOrNull()
        } else {
            val corners = results.map { r ->
                with(r.position) { listOf(topLeft, topRight, bottomRight, bottomLeft) }
                    .map { AimGeometry.Point(it.x.toFloat(), it.y.toFloat()) }
            }
            val frame = AimGeometry.frame(viewW.toFloat(), viewH.toFloat(), resources.displayMetrics.density)
            AimGeometry.pick(corners, imageW, imageH, viewW.toFloat(), viewH.toFloat(), frame, settings.aim)
        }
        val hit = index?.let { results[it] }
        if (hit == null) {
            candidate = null
            candidateCount = 0
            return
        }
        val text = hit.text.orEmpty()
        candidateCount = if (text == candidate) candidateCount + 1 else 1
        candidate = text
        if (candidateCount < settings.confirmReads) return
        candidate = null
        candidateCount = 0
        if (settings.continuous && !configImport) {
            val now = SystemClock.elapsedRealtime()
            val last = lastAccepted[text]
            if (last != null && now - last < settings.duplicateDelayMs) return
            lastAccepted[text] = now
            accepted++
            val count = accepted
            runOnUiThread { deliverContinuous(text, hit.format.name, count) }
        } else if (done.compareAndSet(false, true)) {
            val format = hit.format.name
            runOnUiThread { succeed(text, format) }
        }
    }

    private fun deliverContinuous(text: String, format: String, count: Int) {
        if (isFinishing) return
        feedback()
        CameraScanBus.deliver(ScanResult(text, format, CameraScanner.NAME))
        status.isVisible = true
        status.text = getString(R.string.camera_continuous_status, count, text.take(STATUS_LENGTH))
        restartTimeout()
    }

    private fun succeed(text: String, format: String) {
        if (isFinishing) return
        feedback()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DATA, text).putExtra(EXTRA_FORMAT, format))
        finish()
    }

    private fun restartTimeout() {
        handler.removeCallbacks(timeout)
        if (settings.timeoutSeconds > 0) handler.postDelayed(timeout, settings.timeoutSeconds * 1000L)
    }

    private fun setTorch(on: Boolean) {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        torchOn = on
        cam.cameraControl.enableTorch(on)
        torchButton.isActivated = on
    }

    /** The zoom button cycles 1× → 2× → 3×, limited by the camera. */
    private fun cycleZoom() {
        zoomStep = (zoomStep + 1) % ZOOM_STEPS.size
        setZoom(ZOOM_STEPS[zoomStep])
    }

    private fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value
        val clamped = ratio.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 1f)
        cam.cameraControl.setZoomRatio(clamped)
        zoomStep = ZOOM_STEPS.indexOfLast { it <= clamped + 0.01f }.coerceAtLeast(0)
        zoomButton.text = getString(R.string.camera_zoom_value, clamped)
    }

    private fun fail(message: Int, code: String) {
        Toast.makeText(this, getString(R.string.message_with_code, getString(message), code), Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun feedback() {
        if (settings.beep) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }
        if (settings.vibrate) vibrate()
    }

    @SuppressLint("MissingPermission") // VIBRATE is in the manifest.
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /**
     * Darkens everything outside the aiming frame and draws the frame ([AimGeometry.frame]); a
     * crosshair in the middle for `center`; nothing for `anywhere`.
     */
    private class ViewfinderView(context: Context, private val aim: AimMode) : View(context) {
        private val density = resources.displayMetrics.density
        private val shade = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            color = Color.WHITE
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * density
            color = Color.rgb(255, 59, 48)
        }
        private val rect = RectF()
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            if (aim == AimMode.ANYWHERE) return
            val w = width.toFloat()
            val h = height.toFloat()
            val frame = AimGeometry.frame(w, h, density)
            rect.set(frame.left, frame.top, frame.right, frame.bottom)
            val radius = 16 * density
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, w, h, Path.Direction.CW)
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.drawPath(path, shade)
            canvas.drawRoundRect(rect, radius, radius, stroke)
            if (aim == AimMode.CENTER) {
                val arm = 18 * density
                val cx = frame.centerX
                val cy = frame.centerY
                canvas.drawLine(cx - arm, cy, cx + arm, cy, cross)
                canvas.drawLine(cx, cy - arm, cx, cy + arm, cross)
            }
        }
    }

    /** What opens the camera: the orientation of the caller (a kiosk lock stays), the profile, or a configuration import. */
    data class Request(val orientation: Int, val profileId: String? = null, val configImport: Boolean = false)

    /** Opens the camera scanner; the result is the single code read, `null` when cancelled or in continuous mode. */
    class Contract : ActivityResultContract<Request, ScanResult?>() {
        override fun createIntent(context: Context, input: Request): Intent =
            Intent(context, CameraScanActivity::class.java)
                .putExtra(EXTRA_ORIENTATION, input.orientation)
                .putExtra(EXTRA_PROFILE, input.profileId)
                .putExtra(EXTRA_CONFIG_IMPORT, input.configImport)

        override fun parseResult(resultCode: Int, intent: Intent?): ScanResult? {
            if (resultCode != RESULT_OK) return null
            val data = intent?.getStringExtra(EXTRA_DATA) ?: return null
            return ScanResult(data, intent.getStringExtra(EXTRA_FORMAT), CameraScanner.NAME)
        }
    }

    companion object {
        private const val TAG = "CameraScan"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_ORIENTATION = "orientation"
        private const val EXTRA_PROFILE = "profile"
        private const val EXTRA_CONFIG_IMPORT = "configImport"
        private const val VIBRATION_MS = 60L
        private const val BEEP_MS = 120
        private const val BEEP_VOLUME = 80
        private const val STATUS_LENGTH = 40
        private val ZOOM_STEPS = listOf(1f, 2f, 3f)
    }
}
