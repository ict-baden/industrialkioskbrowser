package de.ictbaden.industrialkioskbrowser.admin

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import de.ictbaden.industrialkioskbrowser.BuildConfig
import de.ictbaden.industrialkioskbrowser.R
import de.ictbaden.industrialkioskbrowser.config.AppConfig
import de.ictbaden.industrialkioskbrowser.config.ConfigCodec
import de.ictbaden.industrialkioskbrowser.config.ConfigException
import de.ictbaden.industrialkioskbrowser.config.ConfigJson
import de.ictbaden.industrialkioskbrowser.config.ConfigStore
import de.ictbaden.industrialkioskbrowser.config.Languages
import de.ictbaden.industrialkioskbrowser.config.PasswordHasher
import de.ictbaden.industrialkioskbrowser.config.Profile
import de.ictbaden.industrialkioskbrowser.config.Resource
import de.ictbaden.industrialkioskbrowser.config.ScanEngine
import de.ictbaden.industrialkioskbrowser.config.ScanTerminator
import de.ictbaden.industrialkioskbrowser.provisioning.Provisioning
import de.ictbaden.industrialkioskbrowser.scanner.CameraScanActivity
import de.ictbaden.industrialkioskbrowser.scanner.ScanResult
import de.ictbaden.industrialkioskbrowser.scanner.ScannerManager
import de.ictbaden.industrialkioskbrowser.ui.AppLanguage
import de.ictbaden.industrialkioskbrowser.ui.Brand
import de.ictbaden.industrialkioskbrowser.ui.Dialogs
import de.ictbaden.industrialkioskbrowser.ui.value
import de.ictbaden.industrialkioskbrowser.web.CustomTrust
import de.ictbaden.industrialkioskbrowser.web.ErrorCodes
import java.text.DateFormat

/** Admin screen (behind the admin password): profiles, design, import/export, security. */
class AdminActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: LinearLayout
    private lateinit var ui: SettingsUi
    private lateinit var scanner: ScannerManager
    private var barcodeImportDialog: AlertDialog? = null
    private var brandColor = 0
    private var rendered = false

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readText(it) }?.let(::confirmImport)
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ConfigJson.write(store.config)) }
        }.onSuccess { toast(getString(R.string.config_exported)) }
            .onFailure { toast(withCode(getString(R.string.export_failed, it.message.orEmpty()), ErrorCodes.EXPORT_FAILED)) }
    }

    /** Configuration QR code read with the camera on devices without a scan engine. */
    private val cameraScan = registerForActivityResult(CameraScanActivity.Contract()) { scan ->
        scan?.let(scanner::deliver)
    }

    private val pickLogo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importLogo)
    }

    private val pickCertificate = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importCertificate)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.action_settings)
            navigationIcon = ContextCompat.getDrawable(this@AdminActivity, R.drawable.ic_back)
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
        scanner = ScannerManager(this, launchCamera = { cameraScan.launch(CameraScanActivity.Request(requestedOrientation, configImport = true)) }, onScan = ::onScan).apply { enabled = false }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::ui.isInitialized && rendered) render()
        rendered = true
        scanner.start()
    }

    override fun onPause() {
        scanner.stop()
        super.onPause()
    }

    private fun render() {
        val config = store.config
        brandColor = Brand.color(this, config)
        ui.brandColor = brandColor
        val onBrand = Brand.onColor(brandColor)
        toolbar.setBackgroundColor(brandColor)
        toolbar.setTitleTextColor(onBrand)
        toolbar.navigationIcon?.setTint(onBrand)
        Brand.applySystemBars(this, brandColor)
        content.removeAllViews()

        section(R.string.section_profiles)
        card(
            config.profiles.map(::profileRow) +
                row(R.drawable.ic_add, getString(R.string.add_profile), getString(R.string.add_profile_summary)) { editProfile(null) },
        )

        val branding = config.global.branding
        section(R.string.section_design)
        card(
            listOf(
                row(R.drawable.ic_globe, getString(R.string.language), languageName(config.global.language)) { pickLanguage() },
                row(R.drawable.ic_title, getString(R.string.title), branding.title ?: getString(R.string.app_name)) { editTitle() },
                row(R.drawable.ic_palette, getString(R.string.brand_color), branding.color ?: getString(R.string.color_default), trailing = swatch(brandColor, 28)) {
                    pickColor(getString(R.string.brand_color), branding.color) { color ->
                        store.update { it.copy(global = it.global.copy(branding = it.global.branding.copy(color = color))) }
                        render()
                    }
                },
                row(R.drawable.ic_image, getString(R.string.logo), getString(if (branding.logo != null) R.string.logo_custom else R.string.logo_default), trailing = logoPreview(config)) {
                    editLogo()
                },
            ),
        )

        section(R.string.section_configuration)
        card(
            listOfNotNull(
                row(R.drawable.ic_upload, getString(R.string.import_file), getString(R.string.import_file_summary)) {
                    importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                row(R.drawable.ic_download, getString(R.string.export_file), getString(R.string.export_file_summary)) {
                    exportFile.launch("industrialkioskbrowser.json")
                },
                row(R.drawable.ic_qr, getString(R.string.export_barcode), getString(R.string.export_barcode_summary)) { showConfigBarcode() },
                row(R.drawable.ic_qr, getString(R.string.import_barcode), getString(R.string.import_barcode_summary)) { startBarcodeImport() },
                Provisioning.lastError(this)?.let {
                    row(R.drawable.ic_info, getString(R.string.last_provisioning_error), withCode(it, ErrorCodes.PROVISIONING), onClick = null)
                },
            ),
        )

        section(R.string.section_security)
        card(
            listOf(
                row(R.drawable.ic_key, getString(R.string.admin_password), passwordState(config.global.adminPasswordHash)) {
                    changePassword(getString(R.string.admin_password)) { hash -> store.update { it.copy(global = it.global.copy(adminPasswordHash = hash)) } }
                },
                row(R.drawable.ic_power, getString(R.string.exit_password), passwordState(config.global.exitPasswordHash)) {
                    changePassword(getString(R.string.exit_password)) { hash -> store.update { it.copy(global = it.global.copy(exitPasswordHash = hash)) } }
                },
                switchRow(R.drawable.ic_shield, getString(R.string.kiosk_mode), getString(R.string.kiosk_mode_summary), config.global.hideExit) { checked ->
                    store.update { it.copy(global = it.global.copy(hideExit = checked)) }
                },
                row(
                    R.drawable.ic_lock,
                    getString(R.string.trusted_certificates),
                    resources.getQuantityString(R.plurals.trusted_certificates_count, config.global.trustedCertificates.size, config.global.trustedCertificates.size),
                ) { showTrustedCertificates() },
            ),
        )

        section(R.string.section_info)
        card(
            listOf(
                row(R.drawable.ic_info, getString(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = null),
                row(R.drawable.ic_scan, getString(R.string.scanner), ScannerManager.displayName(this), onClick = null),
                row(
                    R.drawable.ic_download,
                    getString(R.string.config_file_for_emm),
                    "Android/data/$packageName/files/${Provisioning.CONFIG_FILE_NAME}",
                    onClick = null,
                ),
                // About block required by ict-baden/richtlinien 07.
                row(R.drawable.ic_shield, getString(R.string.about_privacy), getString(R.string.about_privacy_summary)) {
                    openLink(PRIVACY)
                },
                row(R.drawable.ic_info, getString(R.string.about_licence), getString(R.string.about_licence_summary)) {
                    showText(getString(R.string.about_licence), "LICENSE")
                },
                row(R.drawable.ic_info, getString(R.string.about_third_party), "AndroidX, Material Components, Kotlin, zxing-cpp, QR Code generator, Google ML Kit") {
                    showText(getString(R.string.about_third_party), "THIRD-PARTY-NOTICES.txt")
                },
                row(R.drawable.ic_globe, getString(R.string.about_source), SOURCE) { openLink(SOURCE) },
                row(R.drawable.ic_info, getString(R.string.about_support), getString(R.string.about_support_summary)) {
                    openLink("$SOURCE/blob/main/SUPPORT.md")
                },
                row(R.drawable.ic_globe, getString(R.string.about_website), WEBSITE) { openLink(WEBSITE) },
                row(R.drawable.ic_globe, getString(R.string.about_impressum), IMPRESSUM) { openLink(IMPRESSUM) },
            ),
        )
    }

    /** Machine-translated languages are marked as such (they still need review by native speakers). */
    private fun languageName(language: String): String {
        val name = AppLanguage.NAMES[language] ?: return getString(R.string.language_system)
        return if (language in AppLanguage.MACHINE_TRANSLATED) getString(R.string.language_machine_translated, name) else name
    }

    /** Language of this device; stored in `global.language` like every other setting. */
    private fun pickLanguage() {
        val options = listOf(Languages.AUTO) + Languages.SUPPORTED
        val current = options.indexOf(store.config.global.language).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_globe))
            .setTitle(R.string.language)
            .setSingleChoiceItems(options.map(::languageName).toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                store.update { it.copy(global = it.global.copy(language = options[which])) }
                AppLanguage.apply(options[which]) // recreates the open screens in the new language
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** Fixed https links only; locked-down devices often have no browser, then the address is shown. */
    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            MaterialAlertDialogBuilder(this).setTitle(R.string.no_browser).setMessage(url).setPositiveButton(R.string.button_ok, null).show()
        }
    }

    /** Licence texts bundled as assets by the build (see app/build.gradle.kts). */
    private fun showText(title: String, asset: String) {
        val text = assets.open(asset).bufferedReader().use { it.readText() }
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(text).setPositiveButton(R.string.button_ok, null).show()
    }

    // region profiles

    @VisibleForTesting
    internal fun editProfile(existing: Profile?) {
        val profile = existing ?: Profile(id = "", name = "", startUrl = "https://")
        var color = profile.color
        val name = Dialogs.textField(this, getString(R.string.profile_name), profile.name)
        val url = Dialogs.textField(this, getString(R.string.start_url), profile.startUrl, inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val allowList = Dialogs.textField(
            this,
            getString(R.string.allowed_hosts),
            profile.allowList.joinToString(", "),
            helper = getString(R.string.allowed_hosts_summary),
        )
        val colorSwatch = swatch(Brand.parse(color) ?: brandColor, 24)
        lateinit var colorRow: LinearLayout
        colorRow = row(R.drawable.ic_palette, getString(R.string.profile_color), color ?: getString(R.string.profile_color_default), trailing = colorSwatch) {
            pickColor(getString(R.string.profile_color), color) { picked ->
                color = picked
                colorRow.findViewWithTag<TextView>(SUBTITLE_TAG).text = picked ?: getString(R.string.profile_color_default)
                colorSwatch.imageTintList = ColorStateList.valueOf(Brand.parse(picked) ?: brandColor)
            }
        }
        colorRow.setPadding(0, dp(8), 0, dp(8))
        val launchOnStartup = switch(getString(R.string.open_on_start), profile.launchOnStartup)
        val hideToolbar = switch(getString(R.string.hide_toolbar), profile.kiosk.hideToolbar)
        val immersive = switch(getString(R.string.full_screen), profile.kiosk.immersive)
        val keepScreenOn = switch(getString(R.string.keep_screen_on), profile.kiosk.keepScreenOn)
        val scannerEnabled = switch(getString(R.string.scanner_enabled), profile.scanner.enabled)
        // Key names stay as printed on keyboards; only "no key" is translated.
        val terminatorLabels = listOf(getString(R.string.terminator_none), "Tab", "Enter", "Tab + Enter", "Enter + Tab")
        val terminator = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = getString(R.string.send_after_scan)
            addView(MaterialAutoCompleteTextView(context).apply {
                inputType = InputType.TYPE_NULL
                setSimpleItems(terminatorLabels.toTypedArray())
                setText(terminatorLabels[profile.scanner.terminator.ordinal], false)
            })
        }
        val engineLabels = listOf(getString(R.string.scan_engine_auto), getString(R.string.scan_engine_hardware), getString(R.string.scan_engine_camera))
        val engine = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = getString(R.string.scan_engine)
            addView(MaterialAutoCompleteTextView(context).apply {
                inputType = InputType.TYPE_NULL
                setSimpleItems(engineLabels.toTypedArray())
                setText(engineLabels[profile.scanner.engine.ordinal], false)
            })
        }
        val form = Dialogs.form(this, name, url, allowList, colorRow, launchOnStartup, hideToolbar, immersive, keepScreenOn, scannerEnabled, engine, terminator)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.new_profile else R.string.edit_profile)
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val startUrl = url.value.trim()
            if (!startUrl.matches(Regex("^https?://[^/\\s]+.*", RegexOption.IGNORE_CASE))) {
                url.error = getString(R.string.start_url_invalid)
                return@setOnClickListener
            }
            val displayName = name.value.trim().ifEmpty { Brand.host(startUrl) }
            val id = existing?.id ?: uniqueId(store.config, displayName)
            val selectedTerminator = terminatorLabels.indexOf(terminator.value).takeIf { it >= 0 }
                ?.let { ScanTerminator.entries[it] } ?: profile.scanner.terminator
            val selectedEngine = engineLabels.indexOf(engine.value).takeIf { it >= 0 }
                ?.let { ScanEngine.entries[it] } ?: profile.scanner.engine
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
                scanner = profile.scanner.copy(enabled = scannerEnabled.isChecked, terminator = selectedTerminator, engine = selectedEngine),
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
            .setTitle(R.string.delete_profile_title)
            .setMessage(getString(R.string.delete_profile_message, profile.name))
            .setPositiveButton(R.string.button_delete) { _, _ ->
                store.update { it.remove(profile.id) }
                render()
            }
            .setNegativeButton(R.string.button_cancel, null)
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
        val title = Dialogs.textField(this, getString(R.string.title), branding.title.orEmpty(), helper = getString(R.string.title_summary))
        val subtitle = Dialogs.textField(this, getString(R.string.subtitle), branding.subtitle.orEmpty(), helper = getString(R.string.subtitle_summary))
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_title))
            .setTitle(R.string.start_screen_title)
            .setView(Dialogs.form(this, title, subtitle))
            .setPositiveButton(R.string.button_save) { _, _ ->
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
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** Swatch palette plus hex input; [onPicked] receives `null` for "default". */
    @VisibleForTesting
    internal fun pickColor(title: String, current: String?, onPicked: (String?) -> Unit) {
        val hex = Dialogs.textField(this, getString(R.string.color_hex), current.orEmpty(), helper = getString(R.string.color_hex_summary))
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
            text = getString(R.string.color_use_default)
            setOnClickListener {
                hex.editText?.setText("")
                select(null)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_palette))
            .setTitle(title)
            .setView(Dialogs.form(this, grid, hex, reset))
            .setPositiveButton(R.string.button_apply) { _, _ ->
                val value = hex.value.trim().uppercase()
                when {
                    value.isEmpty() -> onPicked(null)
                    value.matches(Regex("#[0-9A-F]{6}")) -> onPicked(value)
                    else -> toast(getString(R.string.color_invalid, value))
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun editLogo() {
        if (store.config.global.branding.logo == null) {
            pickLogo.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_image))
            .setTitle(R.string.logo)
            .setPositiveButton(R.string.button_change) { _, _ -> pickLogo.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
            .setNeutralButton(R.string.button_remove) { _, _ ->
                store.update { config ->
                    config.copy(
                        resources = config.resources - LOGO_RESOURCE,
                        global = config.global.copy(branding = config.global.branding.copy(logo = null)),
                    )
                }
                render()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** List of `global.trustedCertificates`: tap an entry to remove it, or import a certificate file. */
    private fun showTrustedCertificates() {
        val entries = store.config.global.trustedCertificates
        val labels = entries.map(::certificateLabel).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_lock))
            .setTitle(R.string.trusted_certificates)
            .apply {
                if (labels.isEmpty()) setMessage(R.string.trusted_certificates_empty) else setItems(labels) { _, which -> confirmRemoveCertificate(which) }
            }
            .setPositiveButton(R.string.trusted_certificates_import) { _, _ ->
                pickCertificate.launch(arrayOf("application/x-x509-ca-cert", "application/x-pem-file", "application/pkix-cert", "application/octet-stream", "text/plain"))
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun certificateLabel(entry: String): String {
        val trimmed = entry.trim()
        if (trimmed.startsWith(CustomTrust.PIN_PREFIX, ignoreCase = true)) {
            return getString(R.string.trusted_certificate_pin, trimmed.substring(CustomTrust.PIN_PREFIX.length).take(16) + "…")
        }
        if (trimmed.startsWith(AppConfig.RES_PREFIX)) return trimmed
        val cert = runCatching { CustomTrust.parseCertificates(trimmed.toByteArray()).first() }.getOrNull() ?: return "?"
        val name = cert.subjectX500Principal.name.split(',').firstOrNull { it.trim().startsWith("CN=") }?.substringAfter("CN=") ?: cert.subjectX500Principal.name
        return getString(R.string.trusted_certificate_ca, name, DateFormat.getDateInstance().format(cert.notAfter))
    }

    private fun confirmRemoveCertificate(index: Int) {
        val entry = store.config.global.trustedCertificates.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_delete))
            .setTitle(R.string.trusted_certificate_remove)
            .setMessage(certificateLabel(entry))
            .setPositiveButton(R.string.trusted_certificate_remove) { _, _ ->
                store.update { it.copy(global = it.global.copy(trustedCertificates = it.global.trustedCertificates - entry)) }
                render()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** A PEM or DER file; every certificate in it is stored as PEM in the configuration. */
    private fun importCertificate(uri: Uri) {
        val certificates = runCatching {
            val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(bytes.size <= MAX_CERTIFICATE_BYTES)
            CustomTrust.parseCertificates(bytes)
        }.getOrNull()
        if (certificates.isNullOrEmpty()) {
            toast(withCode(getString(R.string.trusted_certificate_invalid), ErrorCodes.CERTIFICATE_IMPORT))
            return
        }
        val pems = certificates.map(CustomTrust::toPem)
        store.update { config ->
            val known = config.global.trustedCertificates
            config.copy(global = config.global.copy(trustedCertificates = known + pems.filter { it !in known }))
        }
        toast(resources.getQuantityString(R.plurals.trusted_certificates_imported, pems.size, pems.size))
        render()
    }

    private fun importLogo(uri: Uri) {
        val bytes = runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
        if (bytes == null || bytes.size > MAX_LOGO_BYTES || BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) {
            toast(getString(R.string.logo_invalid))
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
        val first = Dialogs.textField(this, getString(R.string.new_password), password = true, helper = getString(R.string.new_password_summary))
        val second = Dialogs.textField(this, getString(R.string.repeat_password), password = true)
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_key))
            .setTitle(title)
            .setView(Dialogs.form(this, first, second))
            .setPositiveButton(R.string.button_save) { _, _ ->
                val password = first.value
                when {
                    password != second.value -> toast(getString(R.string.passwords_do_not_match))
                    password.isEmpty() -> save(null).also { toast(getString(R.string.password_removed)) }
                    else -> save(PasswordHasher.hash(password)).also { toast(getString(R.string.password_saved)) }
                }
                render()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** "Export as barcode": QR code of the current configuration, packed when it is long (ConfigCodec). */
    private fun showConfigBarcode() {
        // Without default values: a shorter payload gives a coarser code that scans more easily.
        val payload = ConfigCodec.pack(ConfigJson.writeCompact(store.config))
        val qr = ConfigBarcode.encode(payload)
        if (qr == null) {
            MaterialAlertDialogBuilder(this)
                .setIcon(Dialogs.icon(this, R.drawable.ic_qr))
                .setTitle(R.string.export_barcode)
                .setMessage(withCode(getString(R.string.export_barcode_too_large, payload.length), ErrorCodes.CONFIG_TOO_LARGE))
                .setPositiveButton(R.string.export_file) { _, _ -> exportFile.launch("industrialkioskbrowser.json") }
                .setNegativeButton(R.string.button_cancel, null)
                .show()
            return
        }
        val modules = qr.size + 2 * ConfigBarcode.QUIET_ZONE
        val width = resources.displayMetrics.widthPixels - dp(96)
        val image = ImageView(this).apply {
            setImageBitmap(ConfigBarcode.bitmap(qr, (width / modules).coerceAtLeast(1)))
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.export_barcode)
        }
        val info = TextView(this).apply {
            text = getString(
                R.string.export_barcode_info,
                payload.length,
                qr.version,
                if (payload.startsWith(ConfigCodec.PREFIX)) getString(R.string.export_barcode_packed) else "JSON",
            )
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_qr))
            .setTitle(R.string.export_barcode)
            .setMessage(R.string.export_barcode_message)
            .setView(Dialogs.form(this, image, info))
            .setPositiveButton(R.string.button_ok, null)
            .show()
    }

    private fun startBarcodeImport() {
        scanner.enabled = true
        barcodeImportDialog = MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_qr))
            .setTitle(R.string.import_barcode)
            .setMessage(R.string.import_barcode_message)
            .setNegativeButton(R.string.button_cancel, null)
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
                .setTitle(R.string.import_failed)
                // The detail names JSON fields, so it stays English; the code identifies the message.
                .setMessage(withCode(e.message.orEmpty(), ErrorCodes.IMPORT_INVALID))
                .setPositiveButton(R.string.button_ok, null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(Dialogs.icon(this, R.drawable.ic_upload))
            .setTitle(R.string.import_confirm_title)
            .setMessage(resources.getQuantityString(R.plurals.import_confirm_message, imported.profiles.size, imported.profiles.size))
            .setPositiveButton(R.string.button_import) { _, _ ->
                store.import(imported)
                AppLanguage.apply(store.config.global.language)
                toast(getString(R.string.config_imported))
                render()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }.onFailure { toast(withCode(getString(R.string.read_failed, it.message.orEmpty()), ErrorCodes.FILE_UNREADABLE)) }.getOrNull()

    // endregion

    // region view helpers

    private fun section(@StringRes title: Int) = ui.section(getString(title))

    private fun card(rows: List<View>) = ui.card(rows)

    private fun row(
        @DrawableRes icon: Int,
        title: String,
        subtitle: String?,
        trailing: View? = null,
        onClick: (() -> Unit)?,
    ): LinearLayout = ui.row(icon, title, subtitle, trailing, onClick)

    private fun profileRow(profile: Profile): View {
        val color = Brand.color(this, store.config, profile)
        val delete = ImageButton(this).apply {
            setImageResource(R.drawable.ic_delete)
            imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setBackgroundResource(ripple(borderless = true))
            contentDescription = getString(R.string.button_delete)
            setOnClickListener { confirmDelete(profile) }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(10), dp(8), dp(10))
            setBackgroundResource(ripple())
            setOnClickListener { ProfileSettingsActivity.open(this@AdminActivity, profile.id) }
            addView(TextView(context).apply {
                text = Brand.initial(profile.name)
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_avatar)
                backgroundTintList = ColorStateList.valueOf(color)
                setTextColor(Brand.onColor(color))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(texts(profile.name, Brand.address(this@AdminActivity, profile.startUrl)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(16)
            })
            addView(delete, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
    }

    private fun switchRow(@DrawableRes icon: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        ui.switchRow(icon, title, subtitle, checked, onChange)

    private fun switch(text: String, checked: Boolean) = MaterialSwitch(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(4), 0, dp(4))
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

    private fun texts(title: String, subtitle: String?) = ui.texts(title, subtitle)

    private fun ripple(borderless: Boolean = false): Int = ui.ripple(borderless)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun withCode(message: String, code: String) = getString(R.string.message_with_code, message, code)

    private fun passwordState(hash: String?) = getString(if (hash == null) R.string.password_not_set else R.string.password_set)

    // endregion

    companion object {
        private const val LOGO_RESOURCE = "branding-logo"
        private const val SOURCE = "https://github.com/ict-baden/industrialkioskbrowser"
        private const val WEBSITE = "https://ict-baden.de/produkte/industrial-kiosk-browser/"
        private const val IMPRESSUM = "https://ict-baden.de/impressum/"
        private const val PRIVACY = "https://ict-baden.de/datenschutz/industrial-kiosk-browser/"
        private const val SUBTITLE_TAG = SettingsUi.SUBTITLE_TAG
        private const val MAX_LOGO_BYTES = 1024 * 1024
        private const val MAX_CERTIFICATE_BYTES = 256 * 1024
        private val PALETTE = listOf(
            "#0D47A1", "#1565C0", "#00838F", "#00796B", "#2E7D32", "#558B2F",
            "#F57C00", "#E64A19", "#C62828", "#AD1457", "#6A1B9A", "#37474F",
        )
    }
}
