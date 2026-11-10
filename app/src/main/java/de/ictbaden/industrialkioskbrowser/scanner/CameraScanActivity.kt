package de.ictbaden.industrialkioskbrowser.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
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
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.web.ErrorCodes
import zxingcpp.BarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Full-screen camera scanner for devices without a scan engine (issue #48): live picture, a frame
 * as aiming help, torch and close. The first barcode read ends the screen with the result. Not
 * exported; it runs in the task of the browser, so it also works in lock task (kiosk) mode.
 */
class CameraScanActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var torchButton: ImageButton
    private lateinit var analysisExecutor: ExecutorService
    private val reader by lazy {
        // All formats; rotated and inverted (light on dark) codes too, without the slow "try harder".
        BarcodeReader(BarcodeReader.Options(tryRotate = true, tryInvert = true, tryHarder = false))
    }
    private val done = AtomicBoolean(false)
    private var camera: Camera? = null
    private var torchOn = false
    private var bufferSize: Size? = null
    private var bufferRotation = 0

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else fail(R.string.camera_permission_denied, ErrorCodes.CAMERA_PERMISSION_DENIED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getIntExtra(EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED).let { requestedOrientation = it }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        analysisExecutor = Executors.newSingleThreadExecutor()
        setContentView(buildLayout())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        textureView = TextureView(this)
        val viewfinder = ViewfinderView(this, Color.WHITE)
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
            setOnClickListener { toggleTorch() }
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(torchButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        val hint = TextView(this).apply {
            text = getString(R.string.camera_hint)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            setPadding(dp(24), dp(16), dp(24), dp(32))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(textureView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(viewfinder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updatePadding(top = bars.top + dp(12), left = bars.left + dp(12), right = bars.right + dp(12))
            hint.updatePadding(bottom = bars.bottom + dp(32))
            insets
        }
        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTransform() }
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
            if (textureView.isAvailable) {
                bind(provider)
            } else {
                textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = bind(provider)
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = updateTransform()
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bind(provider: ProcessCameraProvider) {
        if (isFinishing || isDestroyed) return
        val main = ContextCompat.getMainExecutor(this)
        val resolution = ResolutionSelector.Builder()
            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).build()
        preview.setSurfaceProvider(main) { request ->
            val texture = textureView.surfaceTexture
            if (texture == null) {
                request.willNotProvideSurface()
                return@setSurfaceProvider
            }
            texture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
            bufferSize = request.resolution
            val surface = Surface(texture)
            request.setTransformationInfoListener(main) { info ->
                bufferRotation = info.rotationDegrees
                updateTransform()
            }
            request.provideSurface(surface, main) { surface.release() }
            updateTransform()
        }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image ->
            try {
                if (!done.get()) {
                    val hit = reader.read(image).firstOrNull { !it.text.isNullOrEmpty() }
                    if (hit != null && done.compareAndSet(false, true)) {
                        val text = hit.text.orEmpty()
                        val format = hit.format.name
                        runOnUiThread { succeed(text, format) }
                    }
                }
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Binding the camera failed", e)
            fail(R.string.camera_unavailable, ErrorCodes.CAMERA_UNAVAILABLE)
        }
    }

    /**
     * The texture view stretches the camera buffer (sensor orientation) over the whole view: undo the
     * stretch, turn it upright and scale it so it fills the view (centre crop).
     */
    private fun updateTransform() {
        val size = bufferSize ?: return
        val viewWidth = textureView.width.toFloat()
        val viewHeight = textureView.height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return
        textureView.setTransform(previewMatrix(viewWidth, viewHeight, size.width.toFloat(), size.height.toFloat(), bufferRotation))
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        torchButton.isActivated = torchOn
        torchButton.alpha = if (torchOn) 1f else 0.8f
    }

    private fun succeed(text: String, format: String) {
        if (isFinishing) return
        vibrate()
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DATA, text).putExtra(EXTRA_FORMAT, format))
        finish()
    }

    private fun fail(message: Int, code: String) {
        Toast.makeText(this, getString(R.string.message_with_code, getString(message), code), Toast.LENGTH_LONG).show()
        setResult(RESULT_CANCELED)
        finish()
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

    /** Darkens everything outside the aiming frame and draws the frame. */
    private class ViewfinderView(context: Context, private val frameColor: Int) : View(context) {
        private val density = resources.displayMetrics.density
        private val shade = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            color = frameColor
        }
        private val frame = RectF()
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            // Wide enough for 1D barcodes, high enough for QR codes.
            val frameWidth = minOf(w * 0.8f, 520 * density)
            val frameHeight = minOf(frameWidth * 0.75f, h * 0.45f)
            frame.set((w - frameWidth) / 2, (h - frameHeight) / 2, (w + frameWidth) / 2, (h + frameHeight) / 2)
            val radius = 16 * density
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            path.addRect(0f, 0f, w, h, Path.Direction.CW)
            path.addRoundRect(frame, radius, radius, Path.Direction.CW)
            canvas.drawPath(path, shade)
            canvas.drawRoundRect(frame, radius, radius, stroke)
        }
    }

    /** Opens the camera scanner; input is the screen orientation of the caller, so a kiosk lock stays. */
    class Contract : ActivityResultContract<Int, ScanResult?>() {
        override fun createIntent(context: Context, input: Int): Intent =
            Intent(context, CameraScanActivity::class.java).putExtra(EXTRA_ORIENTATION, input)

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
        private const val VIBRATION_MS = 60L

        /**
         * Matrix for a [TextureView] showing a camera buffer of [bufferWidth]×[bufferHeight] that has
         * to be turned by [rotation] degrees to be upright: centre crop, no distortion.
         */
        fun previewMatrix(viewWidth: Float, viewHeight: Float, bufferWidth: Float, bufferHeight: Float, rotation: Int): Matrix {
            val cx = viewWidth / 2
            val cy = viewHeight / 2
            val turned = rotation % 180 != 0
            val rotatedWidth = if (turned) bufferHeight else bufferWidth
            val rotatedHeight = if (turned) bufferWidth else bufferHeight
            val scale = max(viewWidth / rotatedWidth, viewHeight / rotatedHeight)
            return Matrix().apply {
                setScale(bufferWidth / viewWidth, bufferHeight / viewHeight, cx, cy)
                postRotate(rotation.toFloat(), cx, cy)
                postScale(scale, scale, cx, cy)
            }
        }
    }
}
