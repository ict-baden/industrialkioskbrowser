package io.github.pieewiee.enterprisebrowser.admin

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import io.github.pieewiee.enterprisebrowser.BuildConfig
import io.github.pieewiee.enterprisebrowser.R
import io.github.pieewiee.enterprisebrowser.config.AppConfig
import io.github.pieewiee.enterprisebrowser.config.ConfigException
import io.github.pieewiee.enterprisebrowser.config.ConfigJson
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.PasswordHasher
import io.github.pieewiee.enterprisebrowser.config.Profile
import io.github.pieewiee.enterprisebrowser.config.Resource
import io.github.pieewiee.enterprisebrowser.config.ScanTerminator
import io.github.pieewiee.enterprisebrowser.provisioning.Provisioning
import io.github.pieewiee.enterprisebrowser.scanner.ScanResult
import io.github.pieewiee.enterprisebrowser.scanner.ScannerManager
import io.github.pieewiee.enterprisebrowser.ui.Brand
import io.github.pieewiee.enterprisebrowser.ui.Dialogs
import io.github.pieewiee.enterprisebrowser.ui.value

/** Admin screen (behind the admin password): profiles, design, import/export, security. */
class AdminActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private lateinit var scanner: ScannerManager
    private var barcodeImportDialog: AlertDialog? = null
    private var brandColor = 0

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readText(it) }?.let(::confirmImport)
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ConfigJson.write(store.config)) }
        }.onSuccess { toast("Configuration exported") }.onFailure { toast("Export failed: ${it.message}") }
    }

    private val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importLogo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        toolbar = MaterialToolbar(this).apply {
            title = "Settings"
            navigationIcon = ContextCompat.getDrawable(this@AdminActivity, R.drawable.ic_back)
            setNavigationContentDescription(R.string.action_back)
            setNavigationOnClickListener { finish() }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
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
        scanner = ScannerManager(this, ::onScan).apply { enabled = false }
        render()
    }

    override fun onResume() {
        super.onResume()
        scanner.start()
    }

    override fun onPause() {
        scanner.stop()
        super.onPause()
    }

    private fun render() {
        val config = store.config
        brandColor = Brand.color(this, config)
        val onBrand = Brand.onColor(brandColor)
        toolbar.setBackgroundColor(brandColor)
        toolbar.setTitleTextColor(onBrand)
        toolbar.navigationIcon?.setTint(onBrand)
        Brand.applySystemBars(this, brandColor)
        content.removeAllViews()

        section("Profiles")
        card(
            config.profiles.map(::profileRow) +
                row(R.drawable.ic_add, "Add profile", "Start URL, kiosk and scanner") { editProfile(null) },
        )

        val branding = config.global.branding
        section("Design")
        card(
            listOf(
                row(R.drawable.ic_title, "Title", branding.title ?: getString(R.string.app_name)) { editTitle() },
                row(R.drawable.ic_palette, "Brand color", branding.color ?: "Default", trailing = swatch(brandColor, 28)) {
                    pickColor("Brand color", branding.color) { color ->
                        store.update { it.copy(global = it.global.copy(branding = it.global.branding.copy(color = color))) }
                        render()
                    }
                },
                row(R.drawable.ic_image, "Logo", if (branding.logo != null) "Custom logo" else "Default logo", trailing = logoPreview(config)) {
                    editLogo()
                },
            ),
        )

        section("Configuration")
        card(
            listOfNotNull(
                row(R.drawable.ic_upload, "Import from file", "Load a JSON configuration") {
                    importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                row(R.drawable.ic_download, "Export to file", "Save the current configuration") {
                    exportFile.launch("enterprisebrowser.json")
                },
                row(R.drawable.ic_qr, "Import from barcode", "Scan a QR code containing JSON") { startBarcodeImport() },
                Provisioning.lastError(this)?.let { row(R.drawable.ic_info, "Last provisioning error", it, onClick = null) },
            ),
        )

        section("Security")
        card(
            listOf(
                row(R.drawable.ic_key, "Admin password", if (config.global.adminPasswordHash == null) "Not set" else "Set") {
                    changePassword("Admin password") { hash -> store.update { it.copy(global = it.global.copy(adminPasswordHash = hash)) } }
                },
                row(R.drawable.ic_power, "Exit password", if (config.global.exitPasswordHash == null) "Not set" else "Set") {
                    changePassword("Exit password") { hash -> store.update { it.copy(global = it.global.copy(exitPasswordHash = hash)) } }
                },
                switchRow(R.drawable.ic_shield, "Kiosk mode", "Hide exit and profile switching", config.global.hideExit) { checked ->
                    store.update { it.copy(global = it.global.copy(hideExit = checked)) }
                },
            ),
        )

        section("Info")
        card(
            listOf(
                row(R.drawable.ic_info, "Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = null),
                row(R.drawable.ic_scan, "Scanner", scanner.scanner.name, onClick = null),
                row(
                    R.drawable.ic_download,
                    "Configuration file for EMM",
                    "Android/data/$packageName/files/${Provisioning.CONFIG_FILE_NAME}",
                    onClick = null,
                ),
            ),
        )
    }

    // region profiles

    @VisibleForTesting
    internal fun editProfile(existing: Profile?) {
        val profile = existing ?: Profile(id = "", name = "", startUrl = "https://")
        var color = profile.color
        val name = Dialogs.textField(this, "Name", profile.name)
        val url = Dialogs.textField(this, "Start URL", profile.startUrl, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val allowList = Dialogs.textField(
            this,
            "Allowed hosts",
            profile.allowList.joinToString(", "),
            helper = "Comma-separated, empty = all sites allowed",
        )
        val colorSwatch = swatch(Brand.parse(color) ?: brandColor, 24)
        lateinit var colorRow: LinearLayout
        colorRow = row(R.drawable.ic_palette, "Profile color", color ?: "Same as brand color", trailing = colorSwatch) {
            pickColor("Profile color", color) { picked ->
                color = picked
                colorRow.findViewWithTag<TextView>(SUBTITLE_TAG).text = picked ?: "Same as brand color"
                colorSwatch.imageTintList = ColorStateList.valueOf(Brand.parse(picked) ?: brandColor)
            }
        }
        colorRow.setPadding(0, dp(8), 0, dp(8))
        val launchOnStartup = switch("Open on app start", profile.launchOnStartup)
        val hideToolbar = switch("Hide toolbar", profile.kiosk.hideToolbar)
        val immersive = switch("Full screen", profile.kiosk.immersive)
        val keepScreenOn = switch("Keep screen on", profile.kiosk.keepScreenOn)
        val scannerEnabled = switch("Scanner enabled", profile.scanner.enabled)
        val terminatorLabels = listOf("No key", "Tab", "Enter", "Tab + Enter", "Enter + Tab")
        val terminator = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = "Send after scan"
            addView(MaterialAutoCompleteTextView(context).apply {
                inputType = InputType.TYPE_NULL
                setSimpleItems(terminatorLabels.toTypedArray())
                setText(terminatorLabels[profile.scanner.terminator.ordinal], false)
            })
        }
        val form = Dialogs.form(this, name, url, allowList, colorRow, launchOnStartup, hideToolbar, immersive, keepScreenOn, scannerEnabled, terminator)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "New profile" else "Edit profile")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val startUrl = url.value.trim()
            if (!startUrl.matches(Regex("^https?://[^/\\s]+.*", RegexOption.IGNORE_CASE))) {
                url.error = "URL must start with http:// or https://"
                return@setOnClickListener
            }
            val displayName = name.value.trim().ifEmpty { Brand.host(startUrl) }
            val id = existing?.id ?: uniqueId(store.config, displayName)
            val selectedTerminator = terminatorLabels.indexOf(terminator.value).takeIf { it >= 0 }
                ?.let { ScanTerminator.entries[it] } ?: profile.scanner.terminator
            val updated = profile.copy(
                id = id,
                name = displayName,
                startUrl = startUrl,
                color = color,
                allowList = allowList.value.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                launchOnStartup = launchOnStartup.isChecked,
                kiosk = profile.kiosk.copy(
                    hideToolbar = hideToolbar.isChecked,
                    immersive = immersive.isChecked,
                    keepScreenOn = keepScreenOn.isChecked,
                ),
                scanner = profile.scanner.copy(enabled = scannerEnabled.isChecked, terminator = selectedTerminator),
            )
            store.update { config ->
                // Only one profile can auto-launch.
                val others = if (updated.launchOnStartup) {
                    config.profiles.map { if (it.id != id) it.copy(launchOnStartup = false) else it }
                } else {
                    config.profiles
                }
                config.copy(profiles = others).upsert(updated)
            }
            dialog.dismiss()
            render()
        }
    }

    private fun confirmDelete(profile: Profile) {
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_delete))
            .setTitle("Delete profile?")
            .setMessage("\"${profile.name}\" will be removed from this device.")
            .setPositiveButton("Delete") { _, _ ->
                store.update { it.remove(profile.id) }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun uniqueId(config: AppConfig, name: String): String {
        val base = ConfigJson.slug(name).ifEmpty { "profil" }
        var id = base
        var n = 2
        while (config.profile(id) != null) id = "$base-${n++}"
        return id
    }

    // endregion

    // region design

    private fun editTitle() {
        val branding = store.config.global.branding
        val title = Dialogs.textField(this, "Title", branding.title.orEmpty(), helper = "Empty = EnterpriseBrowser")
        val subtitle = Dialogs.textField(this, "Subtitle", branding.subtitle.orEmpty(), helper = "Empty = device model")
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_title))
            .setTitle("Start screen title")
            .setView(Dialogs.form(this, title, subtitle))
            .setPositiveButton("Save") { _, _ ->
                store.update {
                    it.copy(
                        global = it.global.copy(
                            branding = it.global.branding.copy(
                                title = title.value.trim().ifEmpty { null },
                                subtitle = subtitle.value.trim().ifEmpty { null },
                            ),
                        ),
                    )
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Swatch palette plus hex input; [onPicked] receives `null` for "default". */
    @VisibleForTesting
    internal fun pickColor(title: String, current: String?, onPicked: (String?) -> Unit) {
        val hex = Dialogs.textField(this, "Color (#RRGGBB)", current.orEmpty(), helper = "Empty = default color")
        val marks = mutableMapOf<String, ImageView>()
        fun select(color: String?) {
            marks.forEach { (key, mark) -> mark.visibility = if (key.equals(color, ignoreCase = true)) View.VISIBLE else View.INVISIBLE }
        }
        val grid = GridLayout(this).apply {
            columnCount = 6
            for (color in PALETTE) {
                val value = Brand.parse(color)!!
                val mark = ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Brand.onColor(value))
                    scaleType = ImageView.ScaleType.CENTER
                }
                marks[color] = mark
                val cell = FrameLayout(context).apply {
                    contentDescription = color
                    setBackgroundResource(ripple(borderless = true))
                    setOnClickListener {
                        hex.editText?.setText(color)
                        select(color)
                    }
                    addView(View(context).apply {
                        setBackgroundResource(R.drawable.bg_avatar)
                        backgroundTintList = ColorStateList.valueOf(value)
                    }, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
                    addView(mark, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER))
                }
                addView(cell, GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(48)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                })
            }
        }
        select(current)
        val reset = MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Use default color"
            setOnClickListener {
                hex.editText?.setText("")
                select(null)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_palette))
            .setTitle(title)
            .setView(Dialogs.form(this, grid, hex, reset))
            .setPositiveButton("Apply") { _, _ ->
                val value = hex.value.trim().uppercase()
                when {
                    value.isEmpty() -> onPicked(null)
                    value.matches(Regex("#[0-9A-F]{6}")) -> onPicked(value)
                    else -> toast("Invalid color: $value")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editLogo() {
        if (store.config.global.branding.logo == null) {
            pickLogo.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_image))
            .setTitle("Logo")
            .setPositiveButton("Change") { _, _ -> pickLogo.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
            .setNeutralButton("Remove") { _, _ ->
                store.update { config ->
                    config.copy(
                        resources = config.resources - LOGO_RESOURCE,
                        global = config.global.copy(branding = config.global.branding.copy(logo = null)),
                    )
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importLogo(uri: Uri) {
        val bytes = runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.size > MAX_LOGO_BYTES || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
            toast("Invalid image or larger than 1 MB")
            return
        }
        store.update { config ->
            config.copy(
                resources = config.resources + (LOGO_RESOURCE to Resource(bytes, isText = false)),
                global = config.global.copy(branding = config.global.branding.copy(logo = AppConfig.RES_PREFIX + LOGO_RESOURCE)),
            )
        }
        render()
    }

    // endregion

    // region import, export, passwords

    private fun changePassword(title: String, save: (String?) -> Unit) {
        val first = Dialogs.textField(this, "New password", password = true, helper = "Leave empty to remove the password")
        val second = Dialogs.textField(this, "Repeat", password = true)
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_key))
            .setTitle(title)
            .setView(Dialogs.form(this, first, second))
            .setPositiveButton("Save") { _, _ ->
                val password = first.value
                when {
                    password != second.value -> toast("Passwords do not match")
                    password.isEmpty() -> save(null).also { toast("$title removed") }
                    else -> save(PasswordHasher.hash(password)).also { toast("$title saved") }
                }
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startBarcodeImport() {
        scanner.enabled = true
        barcodeImportDialog = MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_qr))
            .setTitle("Import from barcode")
            .setMessage("Scan the configuration barcode (QR code containing JSON) now.")
            .setNegativeButton("Cancel", null)
            .setOnDismissListener {
                scanner.enabled = false
                barcodeImportDialog = null
            }
            .show()
        scanner.softTrigger(true)
    }

    private fun onScan(scan: ScanResult) {
        val dialog = barcodeImportDialog ?: return
        dialog.dismiss()
        confirmImport(scan.data)
    }

    private fun confirmImport(text: String) {
        val imported = try {
            ConfigJson.parse(text)
        } catch (e: ConfigException) {
            MaterialAlertDialogBuilder(this)
                .setIcon(Dialogs.icon(this, R.drawable.ic_info))
                .setTitle("Import failed")
                .setMessage(e.message)
                .setPositiveButton("OK", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_upload))
            .setTitle("Import configuration?")
            .setMessage("${imported.profiles.size} profile(s). The current configuration will be replaced.")
            .setPositiveButton("Import") { _, _ ->
                store.import(imported)
                toast("Configuration imported")
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }.onFailure { toast("Could not read the file: ${it.message}") }.getOrNull()

    // endregion

    // region view helpers

    private fun section(title: String) = content.addView(TextView(this).apply {
        text = title
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        setTextColor(accent())
        setPadding(dp(8), dp(24), dp(8), dp(8))
    })

    private fun card(rows: List<View>) {
        val card = MaterialCardView(this).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                column.addView(MaterialDivider(this).apply {
                    dividerInsetStart = dp(72)
                    dividerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                })
            }
            column.addView(row)
        }
        card.addView(column)
        content.addView(card)
    }

    private fun row(
        @DrawableRes icon: Int,
        title: String,
        subtitle: String?,
        trailing: View? = null,
        onClick: (() -> Unit)?,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        if (onClick != null) {
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        addView(iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(texts(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16)
        })
        trailing?.let { addView(it) }
    }

    private fun profileRow(profile: Profile): View {
        val color = Brand.color(this, store.config, profile)
        val delete = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setBackgroundResource(ripple(borderless = true))
            contentDescription = "Delete"
            setOnClickListener { confirmDelete(profile) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(10), dp(8), dp(10))
            setBackgroundResource(ripple())
            setOnClickListener { editProfile(profile) }
            addView(TextView(context).apply {
                text = Brand.initial(profile.name)
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_avatar)
                backgroundTintList = ColorStateList.valueOf(color)
                setTextColor(Brand.onColor(color))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(texts(profile.name, Brand.address(profile.startUrl)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            })
            addView(delete, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    private fun switchRow(@DrawableRes icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val toggle = MaterialSwitch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        return row(icon, title, subtitle, trailing = toggle) { toggle.toggle() }
    }

    private fun switch(text: String, checked: Boolean) = MaterialSwitch(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun texts(title: String, subtitle: String?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        subtitle?.let {
            addView(TextView(context).apply {
                text = it
                tag = SUBTITLE_TAG
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
    }

    private fun iconCircle(@DrawableRes icon: Int) = ImageView(this).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.CENTER
        imageTintList = ColorStateList.valueOf(accent())
        setBackgroundResource(R.drawable.bg_tile_icon)
        backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(brandColor, 0x1F))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun swatch(color: Int, sizeDp: Int) = ImageView(this).apply {
        setImageResource(R.drawable.bg_avatar)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    private fun logoPreview(config: AppConfig): View {
        val logo = Brand.logo(config)
        return ImageView(this).apply {
            if (logo != null) setImageBitmap(logo) else setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
    }

    /** Readable accent on light surfaces, even for very light brand colors. */
    private fun accent(): Int = if (Brand.isLight(brandColor)) Brand.darker(brandColor, 0.45f) else brandColor

    private fun ripple(borderless: Boolean = false): Int = TypedValue().also {
        theme.resolveAttribute(
            if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground,
            it,
            true,
        )
    }.resourceId

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // endregion

    companion object {
        private const val LOGO_RESOURCE = "branding-logo"
        private const val SUBTITLE_TAG = "subtitle"
        private const val MAX_LOGO_BYTES = 1024 * 1024
        private val PALETTE = listOf(
            "#0D47A1", "#1565C0", "#00838F", "#00796B", "#2E7D32", "#558B2F",
            "#F57C00", "#E64A19", "#C62828", "#AD1457", "#6A1B9A", "#37474F",
        )
    }
}
