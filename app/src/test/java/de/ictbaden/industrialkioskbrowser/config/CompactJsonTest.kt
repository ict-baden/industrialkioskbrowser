package de.ictbaden.industrialkioskbrowser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The barcode export leaves out default values; importing it must give the same configuration. */
class CompactJsonTest {

    private fun assertSameAfterCompact(config: AppConfig) {
        val compact = ConfigJson.writeCompact(config)
        assertEquals(ConfigJson.write(config), ConfigJson.write(ConfigJson.parse(compact)))
        assertTrue("compact is not shorter", compact.length < ConfigJson.write(config).length)
    }

    @Test
    fun `demo configuration`() = assertSameAfterCompact(AppConfig.initial())

    @Test
    fun `every example`() {
        val examples = File("../examples").listFiles { file -> file.extension == "json" }.orEmpty()
        assertTrue(examples.isNotEmpty())
        examples.forEach { assertSameAfterCompact(ConfigJson.parse(it.readText())) }
    }

    @Test
    fun `changed nested values survive, defaults are dropped`() {
        val base = AppConfig.initial()
        val profile = base.profiles.first()
        val changed = base.copy(
            global = base.global.copy(hideExit = true, adminPasswordHash = PasswordHasher.hash("geheim")),
            profiles = listOf(
                profile.copy(
                    allowList = listOf("example.com"),
                    scanner = profile.scanner.copy(
                        terminator = ScanTerminator.TAB,
                        engine = ScanEngine.CAMERA,
                        camera = CameraSettings(aim = AimMode.CENTER, continuous = true, symbologies = listOf("ean13")),
                        data = ScanDataSettings(prefix = "X", stripTrailing = 1),
                    ),
                ),
                Profile(id = "second", name = "Second", startUrl = "https://example.org"),
            ),
        )
        assertSameAfterCompact(changed)
        val compact = ConfigJson.writeCompact(changed)
        assertTrue(compact.contains("\"aim\":\"center\""))
        // A default value such as the duplicate delay does not appear.
        assertTrue(!compact.contains("duplicateDelayMs"))
    }
}
