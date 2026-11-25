package de.ictbaden.industrialkioskbrowser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView

/**
 * The WebView's text-selection menu offers "Share", "Web search" and the text actions of other
 * apps (translate, assistant …). Each of them opens another app – without lock task mode a way out
 * of the kiosk, and "Web search" a way around the allow list. Only cut, copy, paste and select all
 * stay.
 */
object SelectionMenu {
    private val KEPT_IDS = setOf(android.R.id.cut, android.R.id.copy, android.R.id.paste, android.R.id.pasteAsPlainText, android.R.id.selectAll)
    private val KEPT_TITLES = intArrayOf(android.R.string.cut, android.R.string.copy, android.R.string.paste, android.R.string.selectAll)
    private const val KEPT_SHORTCUTS = "xcva"

    /**
     * The WebView gives its items IDs of its own, so they are recognised by the platform's titles
     * (which the WebView uses for these four) or by their keyboard shortcut. An item that is not
     * recognised is removed: a missing "Copy" is the smaller harm than a "Share" that stays.
     */
    fun keeps(context: Context, item: MenuItem): Boolean {
        if (item.intent != null) return false // text action of another app
        if (item.itemId in KEPT_IDS) return true
        val title = item.title?.toString()
        if (title != null && KEPT_TITLES.any { context.getString(it) == title }) return true
        return item.alphabeticShortcut.lowercaseChar() in KEPT_SHORTCUTS
    }

    /** Removes everything [keeps] does not keep; true when the menu changed. */
    fun trim(context: Context, menu: Menu): Boolean {
        var changed = false
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            if (keeps(context, item) || !(item.isVisible || item.isEnabled)) continue
            // removeItem(id) takes the first item with that ID, and unknown items often share ID 0: the item is
            // hidden and disabled instead, the toolbar only shows visible items.
            item.isVisible = false
            item.isEnabled = false
            changed = true
        }
        return changed
    }

    /** The callback of the WebView, with the menu trimmed whenever the WebView fills or refreshes it. */
    fun wrap(context: Context, callback: ActionMode.Callback): ActionMode.Callback = when (callback) {
        is Trimmed -> callback
        is ActionMode.Callback2 -> TrimmedFloating(context, callback)
        else -> TrimmedPrimary(context, callback)
    }

    private interface Trimmed

    private class TrimmedPrimary(private val context: Context, private val inner: ActionMode.Callback) : ActionMode.Callback, Trimmed {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = inner.onCreateActionMode(mode, menu).also { trim(context, menu) }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val prepared = inner.onPrepareActionMode(mode, menu)
            return trim(context, menu) || prepared
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = keeps(context, item) && inner.onActionItemClicked(mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = inner.onDestroyActionMode(mode)
    }

    /** The floating toolbar (Android 6+) asks its callback where the selection is. */
    private class TrimmedFloating(private val context: Context, private val inner: ActionMode.Callback2) : ActionMode.Callback2(), Trimmed {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = inner.onCreateActionMode(mode, menu).also { trim(context, menu) }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val prepared = inner.onPrepareActionMode(mode, menu)
            return trim(context, menu) || prepared
        }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = keeps(context, item) && inner.onActionItemClicked(mode, item)
        override fun onDestroyActionMode(mode: ActionMode) = inner.onDestroyActionMode(mode)
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) = inner.onGetContentRect(mode, view, outRect)
    }
}

/** The browser's WebView: like [WebView], but its text-selection menu goes through [SelectionMenu]. */
@SuppressLint("ViewConstructor") // only created in code, never inflated from a layout
class KioskWebView(context: Context) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(SelectionMenu.wrap(context, callback))

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(SelectionMenu.wrap(context, callback), type)
}
