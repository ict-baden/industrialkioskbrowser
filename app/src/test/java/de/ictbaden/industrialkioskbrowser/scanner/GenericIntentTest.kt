package de.ictbaden.industrialkioskbrowser.scanner

import de.ictbaden.industrialkioskbrowser.config.ConfigJson
import de.ictbaden.industrialkioskbrowser.config.GenericIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericIntentTest {

    @Test
    fun `generic scan broadcast only where needed by default`() {
        assertTrue(ScannerManager.acceptsGenericIntent(GenericIntent.AUTO, NoScanner()))
        assertTrue(ScannerManager.acceptsGenericIntent(GenericIntent.ON, NoScanner()))
        assertFalse(ScannerManager.acceptsGenericIntent(GenericIntent.OFF, NoScanner()))
    }

    @Test
    fun `genericIntent is read and written`() {
        val config = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com","scanner":{"genericIntent":"off"}}]}""")
        assertEquals(GenericIntent.OFF, config.profiles.single().scanner.genericIntent)
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        val default = ConfigJson.parse("""{"profiles":[{"startUrl":"https://a.example.com"}]}""")
        assertEquals(GenericIntent.AUTO, default.profiles.single().scanner.genericIntent)
    }
}
