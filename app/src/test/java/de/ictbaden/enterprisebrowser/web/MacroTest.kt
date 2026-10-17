package de.ictbaden.enterprisebrowser.web

import android.view.KeyEvent
import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.Injection
import de.ictbaden.enterprisebrowser.config.InjectionTiming
import de.ictbaden.enterprisebrowser.config.MatchType
import de.ictbaden.enterprisebrowser.config.Resource
import de.ictbaden.enterprisebrowser.config.ViewportMode
import de.ictbaden.enterprisebrowser.config.ViewportSettings
import de.ictbaden.enterprisebrowser.web.MacroStep.Action
import de.ictbaden.enterprisebrowser.web.MacroStep.Key
import de.ictbaden.enterprisebrowser.web.MacroStep.Pause
import de.ictbaden.enterprisebrowser.web.MacroStep.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTest {

    @Test
    fun `parses text keys and actions`() {
        assertEquals(
            listOf(Text("abc"), Key(KeyEvent.KEYCODE_TAB), Key(KeyEvent.KEYCODE_F1), Text("x"), Action(BrowserAction.HOME)),
            Macro.parse("abc{TAB}{f1}x{NAVIGATE-HOME}"),
        )
    }

    @Test
    fun `supports modifiers pauses hex and braces`() {
        assertEquals(
            listOf(
                Key(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
                Pause(250),
                Pause(40),
                Text("{A}"),
                Key(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON),
            ),
            Macro.parse("{CTRL-C}{PAUSE}{PAUSE:40}{OPEN-BRACE}{HEX:0041}{CLOSE-BRACE}{BACK-TAB}"),
        )
    }

    @Test
    fun `unknown tokens and stray braces are typed literally`() {
        assertEquals(listOf(Text("{FOO}a{b")), Macro.parse("{FOO}a{b"))
        assertEquals(listOf(Action(BrowserAction.SCAN)), Macro.parse("{special:scan}"))
        assertEquals(emptyList<MacroStep>(), Macro.parse(""))
    }

    @Test
    fun `parses tone specs`() {
        assertEquals(listOf(1400 to 200, 1000 to 200), Sounds.parseTones("1400:200:1000:200"))
        assertNull(Sounds.parseTones("1400"))
        assertNull(Sounds.parseTones("a:b"))
    }

    @Test
    fun `fills error page templates case insensitively and escapes`() {
        assertEquals(
            "<h1>Error 404</h1><p>a &lt;b&gt;</p><i>https://x/?a=1&amp;b=\$2</i>",
            StatusPage.fill("<h1>{{Title}}</h1><p>{{message}}</p><i>{{URL}}</i>", "Error 404", "a <b>", "https://x/?a=1&b=\$2"),
        )
    }

    @Test
    fun `builds injection scripts with url checks`() {
        val config = AppConfig(resources = mapOf("a.css" to Resource("p{color:red}".toByteArray(), isText = true)))
        val injections = listOf(
            Injection(urlMatch = "/sap/", matchType = MatchType.CONTAINS, css = "res:a.css"),
            Injection(urlMatch = "^https://x", matchType = MatchType.REGEX, js = "alert('\"')", timing = InjectionTiming.PAGE_LOADED),
        )
        val start = InjectionScripts.build(injections, InjectionTiming.DOCUMENT_START, config)!!
        assertTrue(start.contains("location.href.indexOf(\"/sap/\") >= 0"))
        assertTrue(start.contains("eb.css(\"p{color:red}\")"))
        assertTrue(start.contains("window === window.top"))
        val loaded = InjectionScripts.build(injections, InjectionTiming.PAGE_LOADED, config)!!
        assertTrue(loaded.contains("new RegExp(\"^https://x\")"))
        assertTrue(loaded.contains("eb.js(\"alert('\\\"')\")"))
        assertNull(InjectionScripts.build(emptyList(), InjectionTiming.PAGE_LOADED, config))
    }

    @Test
    fun `viewport script only for fixed width or override`() {
        assertNull(InjectionScripts.viewport(ViewportSettings()))
        assertNull(InjectionScripts.viewport(ViewportSettings(mode = ViewportMode.DEVICE)))
        assertTrue(InjectionScripts.viewport(ViewportSettings(mode = ViewportMode.FIXED, width = 320))!!.contains("\"width=320\""))
        assertTrue(InjectionScripts.viewport(ViewportSettings(metaOverride = "width=device-width"))!!.contains("\"width=device-width\""))
    }
}
