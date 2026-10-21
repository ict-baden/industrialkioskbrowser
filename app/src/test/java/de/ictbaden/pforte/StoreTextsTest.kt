package de.ictbaden.pforte

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Store texts in `fastlane/` stay within the Play limits and contain no broken characters. */
class StoreTextsTest {

    private val languages = File("../fastlane/metadata/android").listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
    private val limits = mapOf("title.txt" to 30, "short_description.txt" to 80, "full_description.txt" to 4000)

    @Test
    fun `store texts are complete, within limits and free of control characters`() {
        assertTrue("no store texts found", languages.size >= 5)
        for (language in languages) {
            for ((name, limit) in limits) {
                val file = File(language, name)
                assertTrue("${language.name}/$name is missing", file.isFile)
                val text = file.readText().trimEnd('\n')
                assertTrue("${language.name}/$name is empty", text.isNotBlank())
                assertTrue("${language.name}/$name has ${text.length} characters, limit $limit", text.length <= limit)
                val bad = text.firstOrNull { it.isISOControl() && it != '\n' }
                assertTrue("${language.name}/$name contains control character U+%04X".format(bad?.code ?: 0), bad == null)
                assertTrue("${language.name}/$name contains the former product name", !text.contains("EnterpriseBrowser"))
            }
        }
    }
}
