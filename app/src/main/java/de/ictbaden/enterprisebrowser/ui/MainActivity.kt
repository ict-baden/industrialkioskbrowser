package de.ictbaden.enterprisebrowser.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.ictbaden.enterprisebrowser.BuildConfig
import de.ictbaden.enterprisebrowser.R
import de.ictbaden.enterprisebrowser.admin.AdminActivity
import de.ictbaden.enterprisebrowser.config.AppConfig
import de.ictbaden.enterprisebrowser.config.ConfigStore
import de.ictbaden.enterprisebrowser.config.Profile
import de.ictbaden.enterprisebrowser.provisioning.Provisioning
import de.ictbaden.enterprisebrowser.scanner.ScannerManager
import de.ictbaden.enterprisebrowser.web.BrowserActivity

/** Start screen: applies provisioning, auto-starts the startup profile and lists all profiles. */
class MainActivity : AppCompatActivity() {

    private val adapter = ProfileAdapter(::open)
    private lateinit var header: LinearLayout
    private lateinit var empty: View

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Provisioning.applyAll(context)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        header = findViewById(R.id.header)
        empty = findViewById(R.id.empty)
        val list = findViewById<RecyclerView>(R.id.profiles).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.updatePadding(top = bars.top, left = bars.left + dp(24), right = bars.right + dp(12))
            list.updatePadding(left = bars.left, right = bars.right)
            findViewById<View>(R.id.footer).updatePadding(bottom = bars.bottom + dp(12))
            insets
        }
        val openSettings = View.OnClickListener {
            Dialogs.requireAdmin(this) { startActivity(Intent(this, AdminActivity::class.java)) }
        }
        findViewById<View>(R.id.settings).setOnClickListener(openSettings)
        findViewById<View>(R.id.emptySettings).setOnClickListener(openSettings)
        findViewById<TextView>(R.id.footer).text =
            getString(R.string.footer, getString(R.string.app_name), BuildConfig.VERSION_NAME, ScannerManager.displayName(this))
        onBackPressedDispatcher.addCallback(this) {
            if (!ConfigStore.get(this@MainActivity).config.global.hideExit) finish()
        }

        Provisioning.applyAll(this)
        if (savedInstanceState == null && !autoLaunched) {
            autoLaunched = true
            ConfigStore.get(this).config.profiles.firstOrNull { it.launchOnStartup }?.let(::open)
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            restrictionsReceiver,
            IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Provisioning.applyAll(this)
        refresh()
    }

    override fun onPause() {
        unregisterReceiver(restrictionsReceiver)
        super.onPause()
    }

    private fun refresh() {
        val config = ConfigStore.get(this).config
        AppLanguage.apply(config.global.language) // a new configuration may set another language
        applyBranding(config)
        adapter.submit(config, config.profiles)
        empty.isVisible = config.profiles.isEmpty()
    }

    private fun applyBranding(config: AppConfig) {
        val branding = config.global.branding
        val color = Brand.color(this, config)
        val onColor = Brand.onColor(color)
        header.background = Brand.headerBackground(color, dp(28).toFloat())
        Brand.applySystemBars(this, color)
        findViewById<TextView>(R.id.title).apply {
            text = branding.title ?: getString(R.string.app_name)
            setTextColor(onColor)
        }
        findViewById<TextView>(R.id.subtitle).apply {
            text = branding.subtitle ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            setTextColor(Brand.onColorSecondary(color))
        }
        findViewById<ImageButton>(R.id.settings).imageTintList = ColorStateList.valueOf(onColor)
        val logo = findViewById<ImageView>(R.id.logo)
        Brand.logo(config)?.let(logo::setImageBitmap) ?: logo.setImageResource(R.mipmap.ic_launcher_round)
    }

    private fun open(profile: Profile) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private class ProfileAdapter(private val onClick: (Profile) -> Unit) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
        private var config = AppConfig()
        private var items: List<Profile> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(config: AppConfig, profiles: List<Profile>) {
            this.config = config
            items = profiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val profile = items[position]
            val context = holder.itemView.context
            val color = Brand.color(context, config, profile)
            holder.avatar.text = Brand.initial(profile.name)
            holder.avatar.backgroundTintList = ColorStateList.valueOf(color)
            holder.avatar.setTextColor(Brand.onColor(color))
            holder.name.text = profile.name
            holder.url.text = Brand.address(holder.itemView.context, profile.startUrl)
            holder.badges.removeAllViews()
            badges(profile).forEach { holder.badges.addView(badge(context, context.getString(it), color)) }
            holder.badges.isVisible = holder.badges.isNotEmpty()
            holder.itemView.setOnClickListener { onClick(profile) }
        }

        override fun getItemCount() = items.size

        private fun badges(profile: Profile) = buildList {
            if (profile.launchOnStartup) add(R.string.badge_autostart)
            if (profile.kiosk.hideToolbar || profile.kiosk.immersive) add(R.string.badge_kiosk)
            if (profile.allowList.isNotEmpty()) add(R.string.badge_allow_list)
            if (!profile.scanner.enabled) add(R.string.badge_scanner_off)
        }

        private fun badge(context: Context, label: String, color: Int) = TextView(context).apply {
            val density = context.resources.displayMetrics.density
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(if (Brand.isLight(color)) Brand.darker(color, 0.55f) else color)
            setBackgroundResource(R.drawable.bg_badge)
            backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x24))
            setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = (6 * density).toInt() }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val avatar: TextView = view.findViewById(R.id.avatar)
            val name: TextView = view.findViewById(R.id.name)
            val url: TextView = view.findViewById(R.id.url)
            val badges: LinearLayout = view.findViewById(R.id.badges)
        }
    }

    companion object {
        /** Auto-launch only once per process, not every time the launcher is recreated. */
        private var autoLaunched = false
    }
}
