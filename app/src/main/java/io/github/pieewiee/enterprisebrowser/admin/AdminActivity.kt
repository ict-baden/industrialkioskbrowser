package io.github.pieewiee.enterprisebrowser.admin

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.pieewiee.enterprisebrowser.BuildConfig
import io.github.pieewiee.enterprisebrowser.config.AppConfig
import io.github.pieewiee.enterprisebrowser.config.ConfigException
import io.github.pieewiee.enterprisebrowser.config.ConfigJson
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.PasswordHasher
import io.github.pieewiee.enterprisebrowser.config.Profile
import io.github.pieewiee.enterprisebrowser.config.ScanTerminator
import io.github.pieewiee.enterprisebrowser.provisioning.Provisioning
import io.github.pieewiee.enterprisebrowser.scanner.ScanResult
import io.github.pieewiee.enterprisebrowser.scanner.ScannerManager
import io.github.pieewiee.enterprisebrowser.ui.Dialogs
import io.github.pieewiee.enterprisebrowser.ui.applySystemInsets

/** Admin screen (behind the admin password): profiles, import/export, passwords. */
class AdminActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private lateinit var content: LinearLayout
    private lateinit var scanner: ScannerManager
    private var barcodeImportDialog: AlertDialog? = null

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readText(it) }?.let(::confirmImport)
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ConfigJson.write(store.config)) }
        }.onSuccess { toast("Konfiguration exportiert") }.onFailure { toast("Export fehlgeschlagen: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Einstellungen"
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        applySystemInsets(scroll)
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
        content.removeAllViews()
        val config = store.config

        heading("Profile")
        config.profiles.forEach { profile ->
            button("${profile.name}\n${profile.startUrl}") { editProfile(profile) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                isAllCaps = false
                setOnLongClickListener { confirmDelete(profile); true }
            }
        }
        button("Profil hinzufügen") { editProfile(null) }
        hint("Antippen zum Bearbeiten, lange drücken zum Löschen.")

        heading("Konfiguration")
        button("Aus Datei importieren") { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        button("In Datei exportieren") { exportFile.launch("enterprisebrowser.json") }
        button("Per Barcode importieren") { startBarcodeImport() }
        Provisioning.lastError(this)?.let { hint("Letzter Provisionierungsfehler: $it") }

        heading("Sicherheit")
        button(if (config.global.adminPasswordHash == null) "Admin-Passwort festlegen" else "Admin-Passwort ändern") {
            changePassword("Admin-Passwort") { hash -> store.update { it.copy(global = it.global.copy(adminPasswordHash = hash)) } }
        }
        button(if (config.global.exitPasswordHash == null) "Exit-Passwort festlegen" else "Exit-Passwort ändern") {
            changePassword("Exit-Passwort") { hash -> store.update { it.copy(global = it.global.copy(exitPasswordHash = hash)) } }
        }
        content.addView(CheckBox(this).apply {
            text = "„Beenden“ und Profilwechsel ausblenden (Kiosk)"
            isChecked = config.global.hideExit
            setOnCheckedChangeListener { _, checked -> store.update { it.copy(global = it.global.copy(hideExit = checked)) } }
        })

        heading("Info")
        hint(
            "Version ${BuildConfig.VERSION_NAME}\nScanner: ${scanner.scanner.name}\n" +
                "Konfigurationsdatei für EMM: Android/data/$packageName/files/${Provisioning.CONFIG_FILE_NAME}",
        )
    }

    private fun editProfile(existing: Profile?) {
        val profile = existing ?: Profile(id = "", name = "", startUrl = "https://")
        val name = field("Name", profile.name)
        val url = field("Start-URL", profile.startUrl, InputType.TYPE_TEXT_VARIATION_URI)
        val allowList = field("Erlaubte Hosts (kommagetrennt, leer = alle)", profile.allowList.joinToString(", "))
        val launchOnStartup = check("Beim App-Start öffnen", profile.launchOnStartup)
        val hideToolbar = check("Toolbar ausblenden", profile.kiosk.hideToolbar)
        val immersive = check("Vollbild (Systemleisten ausblenden)", profile.kiosk.immersive)
        val keepScreenOn = check("Bildschirm anlassen", profile.kiosk.keepScreenOn)
        val scannerEnabled = check("Scanner aktiv", profile.scanner.enabled)
        val terminator = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminActivity, android.R.layout.simple_spinner_dropdown_item, ScanTerminator.entries.map { "Nach Scan: ${it.key}" })
            setSelection(profile.scanner.terminator.ordinal)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            listOf(name, url, allowList, launchOnStartup, hideToolbar, immersive, keepScreenOn, scannerEnabled, terminator).forEach(::addView)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Neues Profil" else "Profil bearbeiten")
            .setView(ScrollView(this).apply { addView(Dialogs.padded(form)) })
            .setPositiveButton("Speichern", null)
            .setNegativeButton("Abbrechen", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val startUrl = url.text.toString().trim()
            if (!startUrl.startsWith("http://") && !startUrl.startsWith("https://") || startUrl.length < 10) {
                url.error = "URL muss mit http:// oder https:// beginnen"
                return@setOnClickListener
            }
            val displayName = name.text.toString().trim().ifEmpty { startUrl }
            val id = existing?.id ?: uniqueId(store.config, displayName)
            val updated = profile.copy(
                id = id,
                name = displayName,
                startUrl = startUrl,
                allowList = allowList.text.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                launchOnStartup = launchOnStartup.isChecked,
                kiosk = profile.kiosk.copy(
                    hideToolbar = hideToolbar.isChecked,
                    immersive = immersive.isChecked,
                    keepScreenOn = keepScreenOn.isChecked,
                ),
                scanner = profile.scanner.copy(
                    enabled = scannerEnabled.isChecked,
                    terminator = ScanTerminator.entries[terminator.selectedItemPosition],
                ),
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
        AlertDialog.Builder(this)
            .setTitle("Profil löschen?")
            .setMessage(profile.name)
            .setPositiveButton("Löschen") { _, _ ->
                store.update { it.remove(profile.id) }
                render()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun changePassword(title: String, save: (String?) -> Unit) {
        val first = Dialogs.passwordField(this, "Neues Passwort (leer = entfernen)")
        val second = Dialogs.passwordField(this, "Wiederholen")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(first)
            addView(second)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(Dialogs.padded(form))
            .setPositiveButton("Speichern") { _, _ ->
                val password = first.text.toString()
                when {
                    password != second.text.toString() -> toast("Passwörter stimmen nicht überein")
                    password.isEmpty() -> save(null).also { toast("$title entfernt") }
                    else -> save(PasswordHasher.hash(password)).also { toast("$title gespeichert") }
                }
                render()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun startBarcodeImport() {
        scanner.enabled = true
        barcodeImportDialog = AlertDialog.Builder(this)
            .setTitle("Per Barcode importieren")
            .setMessage("Jetzt den Konfigurations-Barcode (QR-Code mit JSON) scannen.")
            .setNegativeButton("Abbrechen", null)
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
            AlertDialog.Builder(this).setTitle("Import fehlgeschlagen").setMessage(e.message).setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Konfiguration importieren?")
            .setMessage("${imported.profiles.size} Profil(e). Die aktuelle Konfiguration wird ersetzt.")
            .setPositiveButton("Importieren") { _, _ ->
                store.import(imported)
                toast("Konfiguration importiert")
                render()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }.onFailure { toast("Datei konnte nicht gelesen werden: ${it.message}") }.getOrNull()

    private fun uniqueId(config: AppConfig, name: String): String {
        val base = ConfigJson.slug(name).ifEmpty { "profil" }
        var id = base
        var n = 2
        while (config.profile(id) != null) id = "$base-${n++}"
        return id
    }

    // region view helpers

    private fun heading(text: String) = content.addView(TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(20), 0, dp(6))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    })

    private fun hint(text: String) = content.addView(TextView(this).apply {
        this.text = text
        alpha = 0.7f
        setPadding(0, dp(4), 0, dp(4))
    })

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
        content.addView(this)
    }

    private fun field(hint: String, value: String, variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL) = EditText(this).apply {
        this.hint = hint
        setText(value)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or variation
    }

    private fun check(text: String, checked: Boolean) = CheckBox(this).apply {
        this.text = text
        isChecked = checked
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // endregion
}
