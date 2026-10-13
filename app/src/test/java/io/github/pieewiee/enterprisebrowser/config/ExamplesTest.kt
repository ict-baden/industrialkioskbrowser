package io.github.pieewiee.enterprisebrowser.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every configuration in `examples/` must be accepted by the app as-is. */
class ExamplesTest {

    private val examples = File("../examples").listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }

    @Test
    fun `all example configurations are valid`() {
        assertTrue("no examples found in ${File("../examples").absolutePath}", examples.isNotEmpty())
        for (file in examples) {
            val config = try {
                ConfigJson.parse(file.readText())
            } catch (e: ConfigException) {
                throw AssertionError("${file.name}: ${e.message}", e)
            }
            assertTrue("${file.name} has no profiles", config.profiles.isNotEmpty())
            // Exported form must parse back to the same configuration.
            assertEquals(file.name, config, ConfigJson.parse(ConfigJson.write(config)))
        }
    }
}
