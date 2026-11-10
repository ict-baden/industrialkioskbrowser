package de.ictbaden.industrialkioskbrowser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** `scanner.camera` and `scanner.data` in the JSON format. */
class ScanSettingsJsonTest {

    private fun profile(scanner: String) = """{"profiles":[{"id":"p","startUrl":"https://example.com","scanner":$scanner}]}"""

    private fun parse(scanner: String) = ConfigJson.parse(profile(scanner)).profiles.single().scanner

    @Test
    fun `defaults when the fields are missing`() {
        val s = parse("{}")
        assertEquals(CameraSettings(), s.camera)
        assertEquals(ScanDataSettings(), s.data)
    }

    @Test
    fun `round trip of every new field`() {
        val scanner = ScannerSettings(
            camera = CameraSettings(
                symbologies = listOf("ean13", "qr"),
                aim = AimMode.CENTER,
                continuous = true,
                duplicateDelayMs = 3000,
                confirmReads = 2,
                minLength = 8,
                maxLength = 14,
                zoom = 2.0,
                torch = true,
                inverted = false,
                beep = false,
                vibrate = false,
                timeoutSeconds = 0,
            ),
            data = ScanDataSettings(
                prefix = "#",
                suffix = " ",
                trim = false,
                removeSpaces = true,
                case = TextCase.UPPER,
                stripLeading = 2,
                stripTrailing = 1,
                replace = listOf(Replacement("-", ""), Replacement("^0+(\\d)", "\$1", regex = true)),
                gs1 = Gs1Mode.RAW,
                gsReplacement = "",
                rules = listOf(
                    ScanRule(
                        name = "GTIN",
                        symbologies = listOf("ean13"),
                        minLength = 13,
                        maxLength = 13,
                        startsWith = "40",
                        pattern = "\\d+",
                        prefix = "",
                        suffix = "!",
                        case = TextCase.LOWER,
                        stripLeading = 0,
                        stripTrailing = 1,
                        removeSpaces = false,
                        replace = emptyList(),
                    ),
                    ScanRule(action = RuleAction.DROP),
                ),
                unmatched = RuleAction.DROP,
            ),
        )
        val config = AppConfig(profiles = listOf(Profile(id = "p", name = "P", startUrl = "https://example.com", scanner = scanner)))
        val parsed = ConfigJson.parse(ConfigJson.write(config))
        assertEquals(scanner, parsed.profiles.single().scanner)
    }

    @Test
    fun `null suffix and gs replacement stay null, empty strings stay empty`() {
        val d = parse("""{"data":{"suffix":null,"gsReplacement":"","prefix":""}}""").data
        assertNull(d.suffix)
        assertEquals("", d.gsReplacement)
        val again = ConfigJson.parse(ConfigJson.write(ConfigJson.parse(profile("""{"data":{"suffix":null,"gsReplacement":""}}"""))))
            .profiles.single().scanner.data
        assertNull(again.suffix)
        assertEquals("", again.gsReplacement)
    }

    @Test
    fun `symbology names are case-insensitive`() {
        assertEquals(listOf("ean13", "qr"), parse("""{"camera":{"symbologies":["EAN13","qr","Qr"]}}""").camera.symbologies)
    }

    @Test
    fun `invalid values name the field`() {
        val cases = mapOf(
            """{"camera":{"symbologies":["ean14"]}}""" to "profiles[0].scanner.camera.symbologies",
            """{"camera":{"aim":"middle"}}""" to "profiles[0].scanner.camera.aim",
            """{"camera":{"confirmReads":4}}""" to "profiles[0].scanner.camera.confirmReads",
            """{"camera":{"zoom":5}}""" to "profiles[0].scanner.camera.zoom",
            """{"camera":{"duplicateDelayMs":1.5}}""" to "profiles[0].scanner.camera.duplicateDelayMs",
            """{"data":{"case":"title"}}""" to "profiles[0].scanner.data.case",
            """{"data":{"stripLeading":-1}}""" to "profiles[0].scanner.data.stripLeading",
            """{"data":{"replace":[{"find":"(","regex":true}]}}""" to "profiles[0].scanner.data.replace[0].find",
            """{"data":{"replace":[{"with":"x"}]}}""" to "profiles[0].scanner.data.replace[0].find",
            """{"data":{"rules":[{"pattern":"[a-"}]}}""" to "profiles[0].scanner.data.rules[0].pattern",
            """{"data":{"rules":[{"action":"keep"}]}}""" to "profiles[0].scanner.data.rules[0].action",
            """{"data":{"unmatched":"skip"}}""" to "profiles[0].scanner.data.unmatched",
        )
        for ((scanner, where) in cases) {
            try {
                parse(scanner)
                fail("accepted $scanner")
            } catch (e: ConfigException) {
                assertTrue("${e.message} should name $where", e.message!!.contains(where))
            }
        }
    }
}
