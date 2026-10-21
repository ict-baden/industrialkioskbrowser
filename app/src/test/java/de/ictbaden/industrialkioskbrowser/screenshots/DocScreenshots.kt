package de.ictbaden.industrialkioskbrowser.screenshots

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import de.ictbaden.industrialkioskbrowser.admin.AdminActivity
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.Branding
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.GlobalSettings
import de.ictbaden.industrialkioskbrowser.config.KioskSettings
import de.ictbaden.industrialkioskbrowser.config.Profile
import de.ictbaden.industrialkioskbrowser.config.Resource
import de.ictbaden.industrialkioskbrowser.config.ScannerSettings
import de.ictbaden.industrialkioskbrowser.ui.MainActivity
import de.ictbaden.industrialkioskbrowser.web.BrowserActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration

/**
 * Renders the screens used in the documentation (`docs/images`). Not part of the normal test
 * run; record with `./gradlew recordRoborazziDebug -Pscreenshots`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h760dp-xxhdpi")
class DocScreenshots {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Selects the DataWedge scanner (scan button, scanner name); no device name appears on the images.
        ShadowBuild.setManufacturer("Zebra")
        ShadowBuild.setModel("Handheld")
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
    }

    @Test
    fun launcher() {
        seed(sampleConfig())
        ActivityScenario.launch(MainActivity::class.java).use { capture("launcher") }
    }

    @Test
    fun launcherBranded() {
        seed(
            sampleConfig().copy(
                global = GlobalSettings(
                    branding = Branding(
                        title = "North Logistics Center",
                        subtitle = "Shift A · Hall 3",
                        color = "#00796B",
                        logo = "res:logo.png",
                    ),
                ),
                resources = mapOf("logo.png" to Resource(logoPng(), isText = false)),
            ),
        )
        ActivityScenario.launch(MainActivity::class.java).use { capture("launcher-branded") }
    }

    @Test
    fun settings() {
        seed(sampleConfig())
        ActivityScenario.launch(AdminActivity::class.java).use { capture("settings") }
    }

    @Test
    fun profileEditor() {
        seed(sampleConfig())
        ActivityScenario.launch(AdminActivity::class.java).use { scenario ->
            scenario.onActivity { it.editProfile(sampleConfig().profiles.first()) }
            capture("profile-editor")
        }
    }

    @Test
    fun colorPicker() {
        seed(sampleConfig())
        ActivityScenario.launch(AdminActivity::class.java).use { scenario ->
            scenario.onActivity { it.pickColor("Brand color", "#0D47A1") {} }
            capture("color-picker")
        }
    }

    @Test
    fun browserDemo() {
        seed(sampleConfig())
        browser(AppConfig.DEMO_PROFILE_ID).use { capture("browser-demo-frame") }
    }

    @Test
    fun browserError() {
        seed(sampleConfig())
        browser("goods-receipt").use { capture("browser-wms-frame") }
    }

    @Test
    fun browserMenu() {
        seed(sampleConfig())
        browser(AppConfig.DEMO_PROFILE_ID).use { scenario ->
            scenario.onActivity { it.showMenu() }
            capture("browser-menu-frame")
        }
    }

    private fun browser(profileId: String) = ActivityScenario.launch<BrowserActivity>(
        Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profileId),
    )

    private fun seed(config: AppConfig) {
        ConfigStore.get(context).import(config)
    }

    private fun capture(name: String) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        captureScreenRoboImage("build/screenshots/$name.png", RoborazziOptions())
    }

    // No real device name on the images: the subtitle would otherwise show manufacturer and model.
    private fun sampleConfig() = AppConfig(
        global = GlobalSettings(branding = Branding(subtitle = "Handheld 07 · Warehouse")),
        profiles = listOf(
            Profile(
                id = "goods-receipt",
                name = "Goods receipt",
                startUrl = "https://wms.example.com/goods-receipt",
                allowList = listOf("example.com"),
                launchOnStartup = true,
            ),
            Profile(
                id = "picking",
                name = "Picking",
                startUrl = "https://pick.example.com/app",
                color = "#00796B",
                allowList = listOf("example.com"),
                kiosk = KioskSettings(hideToolbar = true, immersive = true),
            ),
            Profile(
                id = "shipping",
                name = "Shipping & labels",
                startUrl = "https://shipping.example.com",
                color = "#F57C00",
                scanner = ScannerSettings(enabled = false),
            ),
            Profile(id = AppConfig.DEMO_PROFILE_ID, name = "Demo & scanner test", startUrl = AppConfig.DEMO_URL, color = "#6A1B9A"),
        ),
    )

    private fun logoPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(0f, 0f, 192f, 192f), 52f, 52f, paint)
        paint.apply {
            color = 0xFF00796B.toInt()
            textSize = 92f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("NL", 96f, 128f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
