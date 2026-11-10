package de.ictbaden.industrialkioskbrowser.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.AimMode
import de.ictbaden.industrialkioskbrowser.config.CameraSettings
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.GenericIntent
import de.ictbaden.industrialkioskbrowser.config.Gs1Mode
import de.ictbaden.industrialkioskbrowser.config.Profile
import de.ictbaden.industrialkioskbrowser.config.RuleAction
import de.ictbaden.industrialkioskbrowser.config.ScanDataSettings
import de.ictbaden.industrialkioskbrowser.config.ScanEngine
import de.ictbaden.industrialkioskbrowser.config.ScanTerminator
import de.ictbaden.industrialkioskbrowser.config.ScreenOrientation
import de.ictbaden.industrialkioskbrowser.config.SoftKeyboard
import de.ictbaden.industrialkioskbrowser.config.TextCase
import de.ictbaden.industrialkioskbrowser.config.ViewportMode
import de.ictbaden.industrialkioskbrowser.scanner.Symbologies
import de.ictbaden.industrialkioskbrowser.ui.Brand
import de.ictbaden.industrialkioskbrowser.ui.Dialogs
import de.ictbaden.industrialkioskbrowser.ui.value

/**
 * Every setting of one profile that can sensibly be changed on the device, grouped like the JSON
 * format (docs/configuration.md). Lists that do not fit a phone screen (injections, key remapping,
 * hotkeys, replacements and rules of the scan data) are shown with their count only.
 * Opened from [AdminActivity], so the admin password was already checked.
 */
class ProfileSettingsActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private lateinit var ui: SettingsUi
    private lateinit var profileId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        profileId = intent.getStringExtra(EXTRA_PROFILE).orEmpty()
        toolbar = MaterialToolbar(this).apply {
            navigationIcon = ContextCompat.getDrawable(this@ProfileSettingsActivity, R.drawable.ic_back)
            setNavigationContentDescription(R.string.action_back)
            setNavigationOnClickListener { finish() }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        ui = SettingsUi(this, content)
        val scroll = NestedScrollView(this).apply { addView(content) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
        render()
    }

    private fun render() {
        val config = store.config
        val p = config.profile(profileId) ?: run {
            finish()
            return
        }
        val brand = Brand.color(this, config, p)
        ui.brandColor = Brand.color(this, config)
        toolbar.title = p.name
        toolbar.subtitle = getString(R.string.profile_settings_title)
        toolbar.setBackgroundColor(brand)
        toolbar.setTitleTextColor(Brand.onColor(brand))
        toolbar.setSubtitleTextColor(Brand.onColor(brand))
        toolbar.navigationIcon?.setTint(Brand.onColor(brand))
        Brand.applySystemBars(this, brand)
        content.removeAllViews()

        general(p)
        kiosk(p)
        display(p)
        keyboard(p)
        soundsAndErrors(p)
        scanner(p)
        camera(p.scanner.camera)
        scanData(p.scanner.data)
    }

    // region sections

    private fun general(p: Profile) {
        section(R.string.section_general)
        ui.card(
            listOf(
                textRow(R.drawable.ic_title, R.string.profile_name, p.name, p.name) { v ->
                    v.trim().ifEmpty { null }?.let { name -> update { it.copy(name = name) } }
                },
                textRow(R.drawable.ic_globe, R.string.start_url, p.startUrl, p.startUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, validate = { v ->
                    if (v.trim().matches(Regex("^https?://[^/\\s]+.*", RegexOption.IGNORE_CASE))) null else getString(R.string.start_url_invalid)
                }) { v -> update { it.copy(startUrl = v.trim()) } },
                textRow(
                    R.drawable.ic_shield,
                    R.string.allowed_hosts,
                    p.allowList.joinToString(", ").ifEmpty { getString(R.string.allowed_hosts_all) },
                    p.allowList.joinToString(", "),
                    helper = getString(R.string.allowed_hosts_summary),
                ) { v -> update { it.copy(allowList = v.split(',').map(String::trim).filter(String::isNotEmpty)) } },
                textRow(R.drawable.ic_palette, R.string.profile_color, p.color ?: getString(R.string.profile_color_default), p.color.orEmpty(), helper = getString(R.string.color_hex_summary), validate = { v ->
                    if (v.isBlank() || v.trim().matches(Regex("#[0-9A-Fa-f]{6}"))) null else getString(R.string.color_invalid, v)
                }) { v -> update { it.copy(color = v.trim().uppercase().ifEmpty { null }) } },
                switchRow(R.drawable.ic_power, R.string.open_on_start, p.launchOnStartup) { on ->
                    store.update { config ->
                        // Only one profile can auto-launch.
                        config.copy(profiles = config.profiles.map { if (it.id == profileId) it.copy(launchOnStartup = on) else if (on) it.copy(launchOnStartup = false) else it })
                    }
                    render()
                },
                textRow(R.drawable.ic_info, R.string.user_agent, p.userAgent ?: getString(R.string.user_agent_default), p.userAgent.orEmpty()) { v ->
                    update { it.copy(userAgent = v.trim().ifEmpty { null }) }
                },
                switchRow(R.drawable.ic_refresh, R.string.clear_cache_on_start, p.clearCacheOnStart) { on -> update { it.copy(clearCacheOnStart = on) } },
                switchRow(R.drawable.ic_refresh, R.string.clear_cookies_on_start, p.clearCookiesOnStart) { on -> update { it.copy(clearCookiesOnStart = on) } },
                switchRow(R.drawable.ic_upload, R.string.allow_file_upload, p.allowFileUpload) { on -> update { it.copy(allowFileUpload = on) } },
                switchRow(R.drawable.ic_download, R.string.allow_downloads, p.allowDownloads) { on -> update { it.copy(allowDownloads = on) } },
            ),
        )
    }

    private fun kiosk(p: Profile) {
        val k = p.kiosk
        section(R.string.section_kiosk)
        ui.card(
            listOf(
                switchRow(R.drawable.ic_menu, R.string.hide_toolbar, k.hideToolbar) { on -> update { it.copy(kiosk = it.kiosk.copy(hideToolbar = on)) } },
                switchRow(R.drawable.ic_shield, R.string.full_screen, k.immersive) { on -> update { it.copy(kiosk = it.kiosk.copy(immersive = on)) } },
                switchRow(R.drawable.ic_power, R.string.keep_screen_on, k.keepScreenOn) { on -> update { it.copy(kiosk = it.kiosk.copy(keepScreenOn = on)) } },
                choiceRow(
                    R.drawable.ic_swap,
                    R.string.orientation,
                    ScreenOrientation.entries,
                    listOf(R.string.orientation_auto, R.string.orientation_portrait, R.string.orientation_landscape),
                    k.orientation,
                ) { o -> update { it.copy(kiosk = it.kiosk.copy(orientation = o)) } },
            ),
        )
    }

    private fun display(p: Profile) {
        val v = p.viewport
        section(R.string.section_display)
        ui.card(
            listOfNotNull(
                choiceRow(
                    R.drawable.ic_globe,
                    R.string.viewport_mode,
                    ViewportMode.entries,
                    listOf(R.string.viewport_page, R.string.viewport_device, R.string.viewport_fixed),
                    v.mode,
                ) { mode ->
                    // "fixed" needs a width; start with a common one.
                    update { it.copy(viewport = it.viewport.copy(mode = mode, width = if (mode == ViewportMode.FIXED && it.viewport.width == 0) 800 else it.viewport.width)) }
                },
                if (v.mode == ViewportMode.FIXED) {
                    numberRow(R.drawable.ic_globe, R.string.viewport_width, v.width, 1, 4096) { n -> update { it.copy(viewport = it.viewport.copy(width = n)) } }
                } else {
                    null
                },
                numberRow(R.drawable.ic_globe, R.string.initial_scale, v.initialScale, 0, 1000, zeroLabel = R.string.value_auto) { n ->
                    update { it.copy(viewport = it.viewport.copy(initialScale = n)) }
                },
                switchRow(R.drawable.ic_globe, R.string.fit_to_screen, v.fitToScreen) { on -> update { it.copy(viewport = it.viewport.copy(fitToScreen = on)) } },
                numberRow(R.drawable.ic_title, R.string.text_zoom, v.textZoom, 25, 400) { n -> update { it.copy(viewport = it.viewport.copy(textZoom = n)) } },
                switchRow(R.drawable.ic_globe, R.string.pinch_zoom, v.pinchZoom) { on -> update { it.copy(viewport = it.viewport.copy(pinchZoom = on)) } },
                textRow(R.drawable.ic_globe, R.string.viewport_meta, v.metaOverride ?: getString(R.string.value_not_set), v.metaOverride.orEmpty()) { text ->
                    update { it.copy(viewport = it.viewport.copy(metaOverride = text.trim().ifEmpty { null })) }
                },
            ),
        )
    }

    private fun keyboard(p: Profile) {
        val k = p.keyboard
        section(R.string.section_keyboard)
        ui.card(
            listOf(
                choiceRow(
                    R.drawable.ic_keyboard,
                    R.string.soft_keyboard,
                    SoftKeyboard.entries,
                    listOf(R.string.soft_keyboard_system, R.string.soft_keyboard_none),
                    k.soft,
                ) { soft -> update { it.copy(keyboard = it.keyboard.copy(soft = soft)) } },
                switchRow(R.drawable.ic_keyboard, R.string.show_on_focus, k.showOnFocus) { on -> update { it.copy(keyboard = it.keyboard.copy(showOnFocus = on)) } },
                switchRow(R.drawable.ic_keyboard, R.string.show_on_page_load, k.showOnPageLoad) { on -> update { it.copy(keyboard = it.keyboard.copy(showOnPageLoad = on)) } },
                textRow(R.drawable.ic_back, R.string.back_key, k.backKey, k.backKey, helper = getString(R.string.back_key_summary)) { text ->
                    update { it.copy(keyboard = it.keyboard.copy(backKey = text.trim().ifEmpty { "{BACK}" })) }
                },
                switchRow(R.drawable.ic_keyboard, R.string.long_press_digits_f, k.longPressDigitsAsF) { on ->
                    update { it.copy(keyboard = it.keyboard.copy(longPressDigitsAsF = on)) }
                },
                jsonRow(R.drawable.ic_keyboard, R.string.key_remaps, k.remap.size),
                jsonRow(R.drawable.ic_keyboard, R.string.hotkeys, k.hotkeys.size),
            ),
        )
    }

    private fun soundsAndErrors(p: Profile) {
        section(R.string.section_sounds)
        ui.card(
            listOf(
                textRow(R.drawable.ic_scan, R.string.sound_scan_ok, p.sounds.scanOk, p.sounds.scanOk, helper = getString(R.string.sound_summary)) { text ->
                    update { it.copy(sounds = it.sounds.copy(scanOk = text.trim().ifEmpty { "none" })) }
                },
                textRow(R.drawable.ic_info, R.string.sound_error, p.sounds.error, p.sounds.error, helper = getString(R.string.sound_summary)) { text ->
                    update { it.copy(sounds = it.sounds.copy(error = text.trim().ifEmpty { "none" })) }
                },
            ),
        )
        section(R.string.section_error_pages)
        ui.card(
            listOf(
                switchRow(R.drawable.ic_info, R.string.show_http_errors, p.errorPages.showHttpErrors) { on ->
                    update { it.copy(errorPages = it.errorPages.copy(showHttpErrors = on)) }
                },
                ui.row(
                    R.drawable.ic_info,
                    getString(R.string.error_template),
                    getString(if (p.errorPages.template != null) R.string.template_custom else R.string.template_default) + " · " + getString(R.string.set_in_json),
                    onClick = null,
                ),
                jsonRow(R.drawable.ic_settings, R.string.injections, p.injections.size),
            ),
        )
    }

    private fun scanner(p: Profile) {
        val s = p.scanner
        section(R.string.section_scanner)
        ui.card(
            listOf(
                switchRow(R.drawable.ic_scan, R.string.scanner_enabled, s.enabled) { on -> update { it.copy(scanner = it.scanner.copy(enabled = on)) } },
                choiceRow(
                    R.drawable.ic_scan,
                    R.string.scan_engine,
                    ScanEngine.entries,
                    listOf(R.string.scan_engine_auto, R.string.scan_engine_hardware, R.string.scan_engine_camera),
                    s.engine,
                ) { engine -> update { it.copy(scanner = it.scanner.copy(engine = engine)) } },
                ui.row(R.drawable.ic_keyboard, getString(R.string.send_after_scan), terminatorLabels()[s.terminator.ordinal]) {
                    choose(getString(R.string.send_after_scan), terminatorLabels(), s.terminator.ordinal) { i ->
                        update { it.copy(scanner = it.scanner.copy(terminator = ScanTerminator.entries[i])) }
                    }
                },
                switchRow(R.drawable.ic_keyboard, R.string.inject_into_focused_field, s.injectIntoFocusedField) { on ->
                    update { it.copy(scanner = it.scanner.copy(injectIntoFocusedField = on)) }
                },
                choiceRow(
                    R.drawable.ic_swap,
                    R.string.generic_intent,
                    GenericIntent.entries,
                    listOf(R.string.scan_engine_auto, R.string.generic_intent_on, R.string.generic_intent_off),
                    s.genericIntent,
                ) { mode -> update { it.copy(scanner = it.scanner.copy(genericIntent = mode)) } },
            ),
        )
    }

    private fun camera(c: CameraSettings) {
        fun change(transform: (CameraSettings) -> CameraSettings) = update { it.copy(scanner = it.scanner.copy(camera = transform(it.scanner.camera))) }
        section(R.string.section_camera)
        ui.card(
            listOf(
                ui.row(
                    R.drawable.ic_qr,
                    getString(R.string.symbologies),
                    if (c.symbologies.isEmpty()) getString(R.string.symbologies_all) else c.symbologies.joinToString(", ", transform = Symbologies::displayName),
                ) { chooseSymbologies(c.symbologies) { list -> change { it.copy(symbologies = list) } } },
                choiceRow(
                    R.drawable.ic_scan,
                    R.string.aim,
                    AimMode.entries,
                    listOf(R.string.aim_frame, R.string.aim_center, R.string.aim_anywhere),
                    c.aim,
                ) { aim -> change { it.copy(aim = aim) } },
                switchRow(R.drawable.ic_scan, R.string.continuous, c.continuous, R.string.continuous_summary) { on -> change { it.copy(continuous = on) } },
                numberRow(R.drawable.ic_scan, R.string.duplicate_delay, c.duplicateDelayMs, 0, 10_000) { n -> change { it.copy(duplicateDelayMs = n) } },
                ui.row(R.drawable.ic_check, getString(R.string.confirm_reads), confirmLabels()[c.confirmReads - 1]) {
                    choose(getString(R.string.confirm_reads), confirmLabels(), c.confirmReads - 1) { i -> change { it.copy(confirmReads = i + 1) } }
                },
                numberRow(R.drawable.ic_title, R.string.min_length, c.minLength, 0, 9999, zeroLabel = R.string.value_no_limit) { n -> change { it.copy(minLength = n) } },
                numberRow(R.drawable.ic_title, R.string.max_length, c.maxLength, 0, 9999, zeroLabel = R.string.value_no_limit) { n -> change { it.copy(maxLength = n) } },
                ui.row(R.drawable.ic_image, getString(R.string.zoom), getString(R.string.camera_zoom_value, c.zoom.toFloat())) {
                    val labels = ZOOMS.map { getString(R.string.camera_zoom_value, it.toFloat()) }
                    choose(getString(R.string.zoom), labels, ZOOMS.indexOfFirst { it == c.zoom }) { i -> change { it.copy(zoom = ZOOMS[i]) } }
                },
                switchRow(R.drawable.ic_power, R.string.torch_at_start, c.torch) { on -> change { it.copy(torch = on) } },
                switchRow(R.drawable.ic_palette, R.string.inverted, c.inverted) { on -> change { it.copy(inverted = on) } },
                switchRow(R.drawable.ic_info, R.string.beep, c.beep) { on -> change { it.copy(beep = on) } },
                switchRow(R.drawable.ic_info, R.string.vibrate, c.vibrate) { on -> change { it.copy(vibrate = on) } },
                numberRow(R.drawable.ic_close, R.string.camera_timeout, c.timeoutSeconds, 0, 600, zeroLabel = R.string.timeout_never) { n -> change { it.copy(timeoutSeconds = n) } },
            ),
        )
    }

    private fun scanData(d: ScanDataSettings) {
        fun change(transform: (ScanDataSettings) -> ScanDataSettings) = update { it.copy(scanner = it.scanner.copy(data = transform(it.scanner.data))) }
        section(R.string.section_scan_data)
        ui.card(
            listOf(
                textRow(R.drawable.ic_title, R.string.prefix, d.prefix.ifEmpty { getString(R.string.value_empty) }, d.prefix, trim = false) { v -> change { it.copy(prefix = v) } },
                textRow(R.drawable.ic_title, R.string.suffix, d.suffix?.ifEmpty { null } ?: getString(R.string.value_empty), d.suffix.orEmpty(), trim = false) { v ->
                    change { it.copy(suffix = v.ifEmpty { null }) }
                },
                switchRow(R.drawable.ic_title, R.string.trim, d.trim) { on -> change { it.copy(trim = on) } },
                switchRow(R.drawable.ic_title, R.string.remove_spaces, d.removeSpaces) { on -> change { it.copy(removeSpaces = on) } },
                choiceRow(
                    R.drawable.ic_title,
                    R.string.text_case,
                    TextCase.entries,
                    listOf(R.string.case_none, R.string.case_upper, R.string.case_lower),
                    d.case,
                ) { case -> change { it.copy(case = case) } },
                numberRow(R.drawable.ic_title, R.string.strip_leading, d.stripLeading, 0, 999) { n -> change { it.copy(stripLeading = n) } },
                numberRow(R.drawable.ic_title, R.string.strip_trailing, d.stripTrailing, 0, 999) { n -> change { it.copy(stripTrailing = n) } },
                choiceRow(
                    R.drawable.ic_qr,
                    R.string.gs1_format,
                    Gs1Mode.entries,
                    listOf(R.string.gs1_hri, R.string.gs1_raw),
                    d.gs1,
                ) { mode -> change { it.copy(gs1 = mode) } },
                ui.row(R.drawable.ic_qr, getString(R.string.gs_replacement), gsLabel(d.gsReplacement)) { editGsReplacement(d.gsReplacement) { v -> change { it.copy(gsReplacement = v) } } },
                jsonRow(R.drawable.ic_swap, R.string.replacements, d.replace.size),
                jsonRow(R.drawable.ic_settings, R.string.scan_rules, d.rules.size),
                choiceRow(
                    R.drawable.ic_settings,
                    R.string.unmatched,
                    RuleAction.entries,
                    listOf(R.string.unmatched_send, R.string.unmatched_drop),
                    d.unmatched,
                ) { action -> change { it.copy(unmatched = action) } },
            ),
        )
    }

    // endregion

    // region editors

    private fun update(transform: (Profile) -> Profile) {
        store.update { config -> config.profile(profileId)?.let { config.upsert(transform(it)) } ?: config }
        render()
    }

    private fun section(@StringRes title: Int) = ui.section(getString(title))

    private fun switchRow(@DrawableRes icon: Int, @StringRes title: Int, checked: Boolean, @StringRes summary: Int? = null, onChange: (Boolean) -> Unit) =
        ui.switchRow(icon, getString(title), summary?.let(::getString), checked, onChange)

    /** A list that is edited in the JSON configuration only: shows how many entries there are. */
    private fun jsonRow(@DrawableRes icon: Int, @StringRes title: Int, count: Int) =
        ui.row(icon, getString(title), resources.getQuantityString(R.plurals.json_entries, count, count), onClick = null)

    private fun <T : Enum<T>> choiceRow(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        values: List<T>,
        labels: List<Int>,
        current: T,
        onPick: (T) -> Unit,
    ) = ui.row(icon, getString(title), getString(labels[values.indexOf(current)])) {
        choose(getString(title), labels.map(::getString), values.indexOf(current)) { i -> onPick(values[i]) }
    }

    private fun choose(title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                if (which != selected) onPick(which)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun textRow(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        shown: String,
        value: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        helper: String? = null,
        trim: Boolean = true,
        validate: (String) -> String? = { null },
        onSave: (String) -> Unit,
    ) = ui.row(icon, getString(title), shown) {
        val field = Dialogs.textField(this, getString(title), value, inputType = inputType, helper = helper)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(Dialogs.form(this, field))
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = if (trim) field.value.trim() else field.value
            val error = validate(text)
            if (error != null) {
                field.error = error
                return@setOnClickListener
            }
            dialog.dismiss()
            onSave(text)
        }
    }

    private fun numberRow(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        value: Int,
        min: Int,
        max: Int,
        @StringRes zeroLabel: Int? = null,
        onSave: (Int) -> Unit,
    ) = textRow(
        icon,
        title,
        if (value == 0 && zeroLabel != null) getString(zeroLabel) else value.toString(),
        value.toString(),
        InputType.TYPE_CLASS_NUMBER,
        helper = getString(R.string.value_range, min, max),
        validate = { v -> if (v.toIntOrNull()?.let { it in min..max } == true) null else getString(R.string.value_range, min, max) },
    ) { v -> onSave(v.toInt()) }

    private fun chooseSymbologies(current: List<String>, onSave: (List<String>) -> Unit) {
        val names = Symbologies.ALL
        // Empty means all: then every type starts ticked.
        val checked = BooleanArray(names.size) { current.isEmpty() || names[it] in current }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.symbologies)
            .setMultiChoiceItems(names.map(Symbologies::displayName).toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.button_save) { _, _ ->
                val picked = names.filterIndexed { i, _ -> checked[i] }
                // All or none ticked: read every type.
                onSave(if (picked.size == names.size || picked.isEmpty()) emptyList() else picked)
            }
            .setNeutralButton(R.string.symbologies_all) { _, _ -> onSave(emptyList()) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun gsLabel(value: String?) = when (value) {
        null -> getString(R.string.gs_keep)
        "" -> getString(R.string.gs_remove)
        else -> getString(R.string.gs_custom_value, value)
    }

    private fun editGsReplacement(current: String?, onSave: (String?) -> Unit) {
        val labels = listOf(getString(R.string.gs_keep), getString(R.string.gs_remove), getString(R.string.gs_custom))
        val selected = when (current) {
            null -> 0
            "" -> 1
            else -> 2
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.gs_replacement)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> onSave(null)
                    1 -> onSave("")
                    else -> {
                        val field = Dialogs.textField(this, getString(R.string.gs_replacement), current.orEmpty())
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.gs_custom)
                            .setView(Dialogs.form(this, field))
                            .setPositiveButton(R.string.button_save) { _, _ -> onSave(field.value) }
                            .setNegativeButton(R.string.button_cancel, null)
                            .show()
                    }
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    // Key names stay as printed on keyboards; only "no key" is translated.
    private fun terminatorLabels() = listOf(getString(R.string.terminator_none), "Tab", "Enter", "Tab + Enter", "Enter + Tab")

    private fun confirmLabels() = listOf(getString(R.string.confirm_reads_1), getString(R.string.confirm_reads_2), getString(R.string.confirm_reads_3))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    // endregion

    companion object {
        private const val EXTRA_PROFILE = "profile"
        private val ZOOMS = listOf(1.0, 1.5, 2.0, 3.0, 4.0)

        fun open(context: Context, profileId: String) {
            context.startActivity(Intent(context, ProfileSettingsActivity::class.java).putExtra(EXTRA_PROFILE, profileId))
        }
    }
}
