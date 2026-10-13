package io.github.pieewiee.enterprisebrowser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun `parses full configuration and hashes plain passwords`() {
        val config = ConfigJson.parse(
            """
            {
              "version": 1,
              "global": { "adminPassword": "geheim", "hideExit": true },
              "profiles": [{
                "id": "lager",
                "name": "Lager",
                "startUrl": "https://wms.example.com",
                "allowList": ["example.com", " "],
                "launchOnStartup": true,
                "textZoom": 1000,
                "kiosk": { "hideToolbar": true, "orientation": "landscape" },
                "scanner": { "terminator": "tab+enter", "injectIntoFocusedField": false }
              }]
            }
            """,
        )
        assertTrue(PasswordHasher.verify("geheim", config.global.adminPasswordHash))
        assertNull(config.global.exitPasswordHash)
        assertTrue(config.global.hideExit)

        val profile = config.profiles.single()
        assertEquals("lager", profile.id)
        assertEquals(listOf("example.com"), profile.allowList)
        assertTrue(profile.launchOnStartup)
        assertEquals(400, profile.textZoom)
        assertTrue(profile.kiosk.hideToolbar)
        assertTrue(profile.kiosk.keepScreenOn)
        assertEquals(ScreenOrientation.LANDSCAPE, profile.kiosk.orientation)
        assertEquals(ScanTerminator.TAB_ENTER, profile.scanner.terminator)
        assertFalse(profile.scanner.injectIntoFocusedField)
    }

    @Test
    fun `write and parse round trip`() {
        val original = AppConfig(
            global = GlobalSettings(adminPasswordHash = PasswordHasher.hash("x"), hideExit = true),
            profiles = listOf(
                Profile(id = "a", name = "A", startUrl = "https://a.test", allowList = listOf("a.test"), userAgent = "UA {default}"),
                Profile(id = "b", name = "B", startUrl = "http://10.0.0.1", kiosk = KioskSettings(immersive = true)),
            ),
        )
        assertEquals(original, ConfigJson.parse(ConfigJson.write(original)))
    }

    @Test
    fun `derives missing ids from names`() {
        val config = ConfigJson.parse("""{"profiles":[{"name":"Wareneingang Halle 2","startUrl":"https://x.test"}]}""")
        assertEquals("wareneingang-halle-2", config.profiles.single().id)
    }

    @Test
    fun `rejects invalid input`() {
        assertRejected("""not json""")
        assertRejected("""{"profiles":[{"name":"x"}]}""")
        assertRejected("""{"profiles":[{"startUrl":"ftp://x"}]}""")
        assertRejected("""{"profiles":[{"id":"a","startUrl":"https://x"},{"id":"a","startUrl":"https://y"}]}""")
        assertRejected("""{"version":99}""")
        assertRejected("""{"global":{"adminPasswordHash":"plain"}}""")
    }

    @Test
    fun `password hashes verify only the right password`() {
        val hash = PasswordHasher.hash("richtig")
        assertTrue(PasswordHasher.verify("richtig", hash))
        assertFalse(PasswordHasher.verify("falsch", hash))
        assertFalse(PasswordHasher.verify("richtig", null))
        assertFalse(PasswordHasher.verify("richtig", "pbkdf2-sha256\$x\$y\$z"))
    }

    private fun assertRejected(json: String) {
        try {
            ConfigJson.parse(json)
            fail("Expected rejection of $json")
        } catch (_: ConfigException) {
        }
    }
}
