package de.ictbaden.industrialkioskbrowser.web

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File

/** The text-selection menu keeps cut, copy, paste and select all and nothing that opens another app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SelectionMenuTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A menu the way the WebView builds it: IDs of its own, the platform's titles for the standard actions. */
    private fun webViewMenu(): Menu = PopupMenu(context, View(context)).menu.apply {
        add(Menu.NONE, 0x7f0b0101, 1, context.getString(android.R.string.cut)).setAlphabeticShortcut('x')
        add(Menu.NONE, 0x7f0b0102, 2, context.getString(android.R.string.copy)).setAlphabeticShortcut('c')
        add(Menu.NONE, 0x7f0b0103, 3, context.getString(android.R.string.paste)).setAlphabeticShortcut('v')
        add(Menu.NONE, 0x7f0b0104, 4, "Share")
        add(Menu.NONE, 0x7f0b0105, 5, context.getString(android.R.string.selectAll)).setAlphabeticShortcut('a')
        add(Menu.NONE, 0x7f0b0106, 6, "Web search")
        add(Menu.NONE, Menu.NONE, 7, "Translate").intent = Intent(Intent.ACTION_PROCESS_TEXT)
        add(Menu.NONE, Menu.NONE, 8, "Assistant").intent = Intent(Intent.ACTION_PROCESS_TEXT)
        add(Menu.NONE, android.R.id.textAssist, 9, "Call")
    }

    private fun visible(menu: Menu) = (0 until menu.size()).map(menu::getItem).filter { it.isVisible && it.isEnabled }.map { it.title.toString() }

    private val standard get() = listOf(android.R.string.cut, android.R.string.copy, android.R.string.paste, android.R.string.selectAll).map(context::getString)

    @Test
    fun `share, web search and other apps' text actions are removed`() {
        val menu = webViewMenu()
        assertTrue(SelectionMenu.trim(context, menu))
        assertEquals(standard, visible(menu))
        assertFalse("a second pass changes nothing", SelectionMenu.trim(context, menu))
    }

    @Test
    fun `standard actions are recognised by platform id, title or shortcut`() {
        val menu = PopupMenu(context, View(context)).menu
        val byId = menu.add(Menu.NONE, android.R.id.copy, 1, "Kopieren (other label)")
        val byShortcut = menu.add(Menu.NONE, 0x7f0b0102, 2, "复制").setAlphabeticShortcut('C')
        val byTitle = menu.add(Menu.NONE, 0x7f0b0103, 3, context.getString(android.R.string.paste))
        val unknown = menu.add(Menu.NONE, 0x7f0b0199, 4, "Something new")
        val withIntent = menu.add(Menu.NONE, android.R.id.copy, 5, context.getString(android.R.string.copy)).setIntent(Intent(Intent.ACTION_SEND))
        assertTrue(SelectionMenu.keeps(context, byId))
        assertTrue(SelectionMenu.keeps(context, byShortcut))
        assertTrue(SelectionMenu.keeps(context, byTitle))
        assertFalse(SelectionMenu.keeps(context, unknown))
        assertFalse("an item that starts an activity never stays", SelectionMenu.keeps(context, withIntent))
    }

    @Test
    fun `the wrapped callback trims after create and after every refresh and swallows removed items`() {
        val clicked = mutableListOf<String>()
        val inner = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu) = true.also { fill(menu) }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false.also { menu.clear(); fill(menu) }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = true.also { clicked += item.title.toString() }
            override fun onDestroyActionMode(mode: ActionMode) = Unit
            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) = outRect.set(1, 2, 3, 4)
            private fun fill(menu: Menu) {
                menu.add(Menu.NONE, 0x7f0b0102, 1, context.getString(android.R.string.copy))
                menu.add(Menu.NONE, 0x7f0b0104, 2, "Share")
            }
        }
        val wrapped = SelectionMenu.wrap(context, inner)
        assertTrue("the floating toolbar needs a Callback2", wrapped is ActionMode.Callback2)
        assertSame("wrapping twice is harmless", wrapped, SelectionMenu.wrap(context, wrapped))

        val menu = PopupMenu(context, View(context)).menu
        val mode = mode(menu)
        assertTrue(wrapped.onCreateActionMode(mode, menu))
        assertEquals(listOf(context.getString(android.R.string.copy)), visible(menu))
        assertTrue("the menu changed, so the toolbar has to redraw", wrapped.onPrepareActionMode(mode, menu))
        assertEquals(listOf(context.getString(android.R.string.copy)), visible(menu))

        val share = (0 until menu.size()).map(menu::getItem).first { it.title == "Share" }
        assertFalse(wrapped.onActionItemClicked(mode, share))
        assertTrue(wrapped.onActionItemClicked(mode, menu.getItem(0)))
        assertEquals(listOf(context.getString(android.R.string.copy)), clicked)

        val rect = Rect()
        (wrapped as ActionMode.Callback2).onGetContentRect(mode, View(context), rect)
        assertEquals(Rect(1, 2, 3, 4), rect)
    }

    private fun mode(menu: Menu) = object : ActionMode() {
        override fun setTitle(title: CharSequence?) = Unit
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(subtitle: CharSequence?) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun setCustomView(view: View?) = Unit
        override fun invalidate() = Unit
        override fun finish() = Unit
        override fun getMenu() = menu
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
        override fun getMenuInflater() = android.view.MenuInflater(context)
    }

    @Test
    fun `the browser uses the trimmed web view`() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        val intent = Intent(context, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, AppConfig.DEMO_PROFILE_ID)
        val browser = Robolectric.buildActivity(BrowserActivity::class.java, intent).setup().get()
        assertTrue(browser.findViewById<ViewGroup>(R.id.webContainer).getChildAt(0) is KioskWebView)
    }
}
