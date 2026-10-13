package io.github.pieewiee.enterprisebrowser.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.pieewiee.enterprisebrowser.R
import io.github.pieewiee.enterprisebrowser.admin.AdminActivity
import io.github.pieewiee.enterprisebrowser.config.ConfigStore
import io.github.pieewiee.enterprisebrowser.config.Profile
import io.github.pieewiee.enterprisebrowser.provisioning.Provisioning
import io.github.pieewiee.enterprisebrowser.web.BrowserActivity

/** Launcher: applies provisioning, auto-starts the startup profile and lists all profiles. */
class MainActivity : AppCompatActivity() {

    private val adapter = ProfileAdapter(::open)
    private lateinit var empty: TextView

    private val restrictionsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Provisioning.applyAll(context)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemInsets(findViewById(R.id.root))
        empty = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.profiles).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<View>(R.id.settings).setOnClickListener {
            Dialogs.requireAdmin(this) { startActivity(Intent(this, AdminActivity::class.java)) }
        }
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
        val profiles = ConfigStore.get(this).config.profiles
        adapter.submit(profiles)
        empty.isVisible = profiles.isEmpty()
    }

    private fun open(profile: Profile) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_PROFILE_ID, profile.id))
    }

    private class ProfileAdapter(private val onClick: (Profile) -> Unit) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
        private var items: List<Profile> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(profiles: List<Profile>) {
            items = profiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val profile = items[position]
            holder.name.text = profile.name
            holder.url.text = profile.startUrl
            holder.itemView.setOnClickListener { onClick(profile) }
        }

        override fun getItemCount() = items.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.name)
            val url: TextView = view.findViewById(R.id.url)
        }
    }

    companion object {
        /** Auto-launch only once per process, not every time the launcher is recreated. */
        private var autoLaunched = false
    }
}
