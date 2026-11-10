package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.ConfigJson
import de.ictbaden.industrialkioskbrowser.config.GenericIntent
import de.ictbaden.industrialkioskbrowser.config.ScanEngine
import de.ictbaden.industrialkioskbrowser.scanner.ScanEngineDetector.Choice
import de.ictbaden.industrialkioskbrowser.scanner.ScanEngineDetector.ScannerInfo
import de.ictbaden.industrialkioskbrowser.scanner.ScanEngineDetector.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEngineTest {

    /** What DataWedge 15 lists on a TC22/HC20 with SE4720: every supported scanner, connected or not. */
    private val hc20 = listOf(
        ScannerInfo("Camera scanner", "INTERNAL_CAMERA", false),
        ScannerInfo("2D Barcode Imager", "INTERNAL_IMAGER", false),
        ScannerInfo("Bluetooth Scanner", "BLUETOOTH_SSI", false),
        ScannerInfo("RS6000 Bluetooth Scanner", "BLUETOOTH_RS6000", false),
        ScannerInfo("RS5100 Bluetooth Scanner", "BLUETOOTH_RS5100", false),
        ScannerInfo("Zebra Bluetooth Scanner", "BLUETOOTH_ZEBRA", false),
        ScannerInfo("Zebra USB Scanner", "USB_ZEBRA", false),
        ScannerInfo("Zebra USB Cradle", "USB_ZEBRACRADLE", false),
        ScannerInfo("MP7000 USB Scanner", "USB_TGCS_MP7000", false),
        ScannerInfo("Zebra SNAPI USB Scanner", "USB_SNAPI_ZEBRA", false),
    )

    @Test
    fun `hc20 list has a scan engine`() {
        assertTrue(ScanEngineDetector.hasScanEngine(hc20))
    }

    @Test
    fun `same list without the imager has none`() {
        assertFalse(ScanEngineDetector.hasScanEngine(hc20.filterNot { it.identifier == "INTERNAL_IMAGER" }))
    }

    @Test
    fun `a connected external scanner counts, the camera never`() {
        val withoutImager = hc20.filterNot { it.identifier == "INTERNAL_IMAGER" }
        val ringScanner = withoutImager.map { if (it.identifier == "BLUETOOTH_RS6000") it.copy(connected = true) else it }
        assertTrue(ScanEngineDetector.hasScanEngine(ringScanner))
        assertFalse(ScanEngineDetector.hasScanEngine(listOf(ScannerInfo("Camera scanner", "INTERNAL_CAMERA", true))))
        assertTrue(ScanEngineDetector.hasScanEngine(listOf(ScannerInfo("1D Laser", "INTERNAL_LASER", null))))
        assertFalse(ScanEngineDetector.hasScanEngine(emptyList()))
    }

    @Test
    fun `auto uses the scan engine unless detection found none`() {
        fun auto(vendor: Boolean, state: State, camera: Boolean) = ScanEngineDetector.choose(ScanEngine.AUTO, vendor, state, camera)
        assertEquals(Choice.HARDWARE, auto(vendor = true, State.UNKNOWN, camera = true))
        assertEquals(Choice.HARDWARE, auto(vendor = true, State.PRESENT, camera = true))
        assertEquals(Choice.CAMERA, auto(vendor = true, State.ABSENT, camera = true))
        assertEquals(Choice.HARDWARE, auto(vendor = true, State.ABSENT, camera = false))
        assertEquals(Choice.CAMERA, auto(vendor = false, State.ABSENT, camera = true))
        assertEquals(Choice.NONE, auto(vendor = false, State.ABSENT, camera = false))
    }

    @Test
    fun `fixed engines`() {
        assertEquals(Choice.CAMERA, ScanEngineDetector.choose(ScanEngine.CAMERA, true, State.PRESENT, true))
        assertEquals(Choice.NONE, ScanEngineDetector.choose(ScanEngine.CAMERA, true, State.PRESENT, false))
        assertEquals(Choice.HARDWARE, ScanEngineDetector.choose(ScanEngine.HARDWARE, true, State.ABSENT, true))
        assertEquals(Choice.NONE, ScanEngineDetector.choose(ScanEngine.HARDWARE, false, State.ABSENT, true))
    }

    @Test
    fun `vendor by manufacturer`() {
        assertEquals(ScanEngineDetector.Vendor.ZEBRA, ScanEngineDetector.vendor("Zebra Technologies"))
        assertEquals(ScanEngineDetector.Vendor.HONEYWELL, ScanEngineDetector.vendor("Honeywell"))
        assertEquals(ScanEngineDetector.Vendor.OTHER, ScanEngineDetector.vendor("samsung"))
    }

    @Test
    fun `camera accepts the generic broadcast in auto like no scanner`() {
        assertTrue(ScannerManager.acceptsGenericIntent(GenericIntent.AUTO, CameraScanner {}))
        assertFalse(ScannerManager.acceptsGenericIntent(GenericIntent.OFF, CameraScanner {}))
    }

    @Test
    fun `camera trigger opens the camera, stop does not`() {
        var opened = 0
        val camera = CameraScanner { opened++ }
        camera.softTrigger(true)
        camera.softTrigger(null)
        camera.softTrigger(false)
        assertEquals(2, opened)
        camera.setEnabled(false)
        camera.softTrigger(true)
        assertEquals(2, opened)
    }

    @Test
    fun `engine is read and written`() {
        val config = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com","scanner":{"engine":"camera"}}]}""")
        assertEquals(ScanEngine.CAMERA, config.profiles.single().scanner.engine)
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        val hardware = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com","scanner":{"engine":"HARDWARE"}}]}""")
        assertEquals(ScanEngine.HARDWARE, hardware.profiles.single().scanner.engine)
        val default = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com"}]}""")
        assertEquals(ScanEngine.AUTO, default.profiles.single().scanner.engine)
        val unknown = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com","scanner":{"engine":"laser"}}]}""")
        assertEquals(ScanEngine.AUTO, unknown.profiles.single().scanner.engine)
    }
}
