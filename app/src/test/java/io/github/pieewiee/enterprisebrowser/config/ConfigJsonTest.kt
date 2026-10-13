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
        assertEquals(400, profile.viewport.textZoom)
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
            resources = mapOf(
                "fix.css" to Resource("body{}".toByteArray(), isText = true),
                "beep.ogg" to Resource(byteArrayOf(0, 1, -1, 42), isText = false),
            ),
            profiles = listOf(
                Profile(id = "a", name = "A", startUrl = "https://a.test", allowList = listOf("a.test"), userAgent = "UA {default}"),
                Profile(
                    id = "b",
                    name = "B",
                    startUrl = "http://10.0.0.1",
                    kiosk = KioskSettings(immersive = true),
                    viewport = ViewportSettings(mode = ViewportMode.FIXED, width = 320, initialScale = 150, pinchZoom = false),
                    keyboard = KeyboardSettings(
                        soft = SoftKeyboard.NONE,
                        showOnFocus = false,
                        backKey = "{F3}",
                        remap = listOf(KeyRemap("BUTTON_L1", "{TAB}"), KeyRemap("F7", "")),
                        hotkeys = mapOf("toggleKeyboard" to "F12"),
                        longPressDigitsAsF = true,
                    ),
                    injections = listOf(
                        Injection(urlMatch = "/sap/", matchType = MatchType.CONTAINS, css = "res:fix.css"),
                        Injection(timing = InjectionTiming.PAGE_LOADED, html = "<b>x</b>", htmlPosition = HtmlPosition.HEAD, sourceContains = "form"),
                    ),
                    errorPages = ErrorPageSettings(template = "<p>{{message}}</p>", showHttpErrors = true),
                    sounds = SoundSettings(scanOk = "res:beep.ogg", error = "tone:1400:200"),
                ),
            ),
        )
        assertEquals(original, ConfigJson.parse(ConfigJson.write(original)))
    }

    @Test
    fun `parses branding and profile colors`() {
        val config = ConfigJson.parse(
            """
            {
              "resources": { "logo.png": { "base64": "iVBORw0KGgo=" } },
              "global": { "branding": { "title": "Lager Nord", "subtitle": "Halle 3", "color": "#00796b", "logo": "res:logo.png" } },
              "profiles": [{ "startUrl": "https://x.test", "color": "#f57c00" }]
            }
            """,
        )
        assertEquals(Branding("Lager Nord", "Halle 3", "#00796B", "res:logo.png"), config.global.branding)
        assertEquals("#F57C00", config.profiles.single().color)
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
    }

    @Test
    fun `version 1 zoom fields map into viewport`() {
        val profile = ConfigJson.parse("""{"version":1,"profiles":[{"startUrl":"https://x.test","textZoom":150,"pinchZoom":false}]}""")
            .profiles.single()
        assertEquals(150, profile.viewport.textZoom)
        assertFalse(profile.viewport.pinchZoom)
        assertEquals(ViewportMode.PAGE, profile.viewport.mode)
    }

    @Test
    fun `parses keyboard injections and resources`() {
        val config = ConfigJson.parse(
            """
            {
              "resources": { "sap.css": { "text": "input{font-size:20px}" } },
              "profiles": [{
                "startUrl": "https://sap.test",
                "keyboard": { "soft": "system", "showOnFocus": false, "remap": [{ "key": "F5", "send": "{NAVIGATE-REFRESH}" }],
                              "hotkeys": { "toggleKeyboard": "F12", "menu": null } },
                "injections": [{ "match": { "url": "^https://sap\\.test/a", "type": "regex" }, "css": "res:sap.css" }]
              }]
            }
            """,
        )
        val profile = config.profiles.single()
        assertEquals(mapOf("toggleKeyboard" to "F12"), profile.keyboard.hotkeys)
        assertEquals(KeyRemap("F5", "{NAVIGATE-REFRESH}"), profile.keyboard.remap.single())
        assertEquals(MatchType.REGEX, profile.injections.single().matchType)
        assertEquals(InjectionTiming.DOCUMENT_START, profile.injections.single().timing)
        assertEquals("input{font-size:20px}", config.content(profile.injections.single().css))
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
        assertRejected("""{"profiles":[{"startUrl":"https://x","viewport":{"mode":"fixed"}}]}""")
        assertRejected("""{"profiles":[{"startUrl":"https://x","injections":[{"css":"res:missing.css"}]}]}""")
        assertRejected("""{"profiles":[{"startUrl":"https://x","injections":[{"match":{"url":"(","type":"regex"},"css":"x"}]}]}""")
        assertRejected("""{"profiles":[{"startUrl":"https://x","injections":[{"match":{"url":"/a"}}]}]}""")
        assertRejected("""{"profiles":[{"startUrl":"https://x","keyboard":{"hotkeys":{"fly":"F1"}}}]}""")
        assertRejected("""{"resources":{"../evil":{"text":"x"}}}""")
        assertRejected("""{"global":{"branding":{"color":"blue"}}}""")
        assertRejected("""{"global":{"branding":{"logo":"res:missing.png"}}}""")
        assertRejected("""{"global":{"branding":{"logo":"https://x.test/logo.png"}}}""")
        assertRejected("""{"profiles":[{"startUrl":"https://x","color":"#12345"}]}""")
        assertRejected("""{"resources":{"a.bin":{"base64":"!!!"}}}""")
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
