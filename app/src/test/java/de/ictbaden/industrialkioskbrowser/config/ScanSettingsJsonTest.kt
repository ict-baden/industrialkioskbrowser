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
    fun `ocr and nfc defaults and round trip`() {
        val empty = parse("{}")
        assertEquals(OcrSettings(), empty.ocr)
        assertEquals(NfcSettings(), empty.nfc)
        val scanner = ScannerSettings(
            ocr = OcrSettings(
                enabled = false,
                mode = OcrMode.PATTERN,
                pattern = "[A-Z]{2}\\d{6}",
                startInOcr = true,
                confirmReads = 3,
                minLength = 4,
                maxLength = 20,
                charset = OcrCharset.UPPER,
            ),
            nfc = NfcSettings(
                enabled = false,
                read = NfcRead.UID,
                uidFormat = UidFormat.DECIMAL,
                uidReversed = true,
                uidSeparator = ":",
                ndefRecord = NdefRecordMode.ALL_LINES,
                beep = false,
                vibrate = false,
            ),
            data = ScanDataSettings(rules = listOf(ScanRule(symbologies = listOf("ocr", "nfc"), prefix = "T:"))),
        )
        val config = AppConfig(profiles = listOf(Profile(id = "p", name = "P", startUrl = "https://example.com", scanner = scanner)))
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        assertEquals(config, ConfigJson.parse(ConfigJson.writeCompact(config)))
    }

    @Test
    fun `ocr pattern mode needs a valid pattern`() {
        for ((scanner, where) in mapOf(
            """{"ocr":{"mode":"pattern"}}""" to "profiles[0].scanner.ocr.pattern",
            """{"ocr":{"pattern":"[a-"}}""" to "profiles[0].scanner.ocr.pattern",
            """{"ocr":{"confirmReads":6}}""" to "profiles[0].scanner.ocr.confirmReads",
            """{"ocr":{"charset":"latin"}}""" to "profiles[0].scanner.ocr.charset",
            """{"nfc":{"read":"all"}}""" to "profiles[0].scanner.nfc.read",
            """{"nfc":{"uidFormat":"octal"}}""" to "profiles[0].scanner.nfc.uidFormat",
        )) {
            try {
                parse(scanner)
                fail("accepted $scanner")
            } catch (e: ConfigException) {
                assertTrue("${e.message} should name $where", e.message!!.contains(where))
            }
        }
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
            """{"queue":{"maxPending":0}}""" to "profiles[0].scanner.queue.maxPending",
            """{"queue":{"timeoutMs":500}}""" to "profiles[0].scanner.queue.timeoutMs",
            """{"queue":{"navigationGraceMs":-1}}""" to "profiles[0].scanner.queue.navigationGraceMs",
            """{"queue":{"settleMs":9000}}""" to "profiles[0].scanner.queue.settleMs",
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

    @Test
    fun `queue defaults, round trip and compact export`() {
        // Older configurations have no scanner.queue: the queue is on with the defaults.
        assertEquals(ScanQueueSettings(), parse("{}").queue)
        assertEquals(true, parse("{}").queue.enabled)
        assertEquals(true, parse("{}").queue.waitForFieldClear)
        assertEquals(ScanQueueSettings(enabled = false, maxPending = 20, timeoutMs = 15_000), parse("""{"queue":{"enabled":false}}""").queue)

        val queue = ScanQueueSettings(enabled = false, maxPending = 5, timeoutMs = 30_000, navigationGraceMs = 800, settleMs = 0, waitForFieldClear = false)
        val config = AppConfig(profiles = listOf(Profile(id = "p", name = "P", startUrl = "https://example.com", scanner = ScannerSettings(queue = queue))))
        assertEquals(queue, ConfigJson.parse(ConfigJson.write(config)).profiles.single().scanner.queue)
        assertEquals(queue, ConfigJson.parse(ConfigJson.writeCompact(config)).profiles.single().scanner.queue)

        val defaults = AppConfig(profiles = listOf(Profile(id = "p", name = "P", startUrl = "https://example.com")))
        assertTrue(ConfigJson.write(defaults).contains("\"navigationGraceMs\""))
        assertTrue(!ConfigJson.writeCompact(defaults).contains("queue"))
        val onlyTimeout = defaults.copy(profiles = listOf(defaults.profiles.single().copy(scanner = ScannerSettings(queue = ScanQueueSettings(timeoutMs = 20_000)))))
        assertTrue(ConfigJson.writeCompact(onlyTimeout).contains("\"queue\":{\"timeoutMs\":20000}"))
    }
}
