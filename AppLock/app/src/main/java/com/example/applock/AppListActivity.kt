package com.example.applock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

data class AppRow(val pkg: String, val label: String)

class AppListActivity : AppCompatActivity() {

    private lateinit var store: PinStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_list)
        store = PinStore(this)

        val list = findViewById<RecyclerView>(R.id.appList)
        val progress = findViewById<ProgressBar>(R.id.progress)
        list.layoutManager = LinearLayoutManager(this)

        val ownPkg = packageName
        Thread {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val rows = pm.queryIntentActivities(launcher, 0)
                .map { AppRow(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != ownPkg }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                list.adapter = AppAdapter(rows, pm, store)
            }
        }.start()
    }

    /** Leaving the screen (home, recents) ends the authenticated session. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations) {
            MainActivity.authed = false
            finish()
        }
    }
}

class AppAdapter(
    private val rows: List<AppRow>,
    private val pm: PackageManager,
    private val store: PinStore
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val toggle: SwitchMaterial = view.findViewById(R.id.appSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.name.text = row.label
        try {
            holder.icon.setImageDrawable(pm.getApplicationIcon(row.pkg))
        } catch (_: Exception) {
            holder.icon.setImageResource(R.drawable.ic_lock_app)
        }
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = store.isLocked(row.pkg)
        holder.toggle.setOnCheckedChangeListener { _, checked -> store.setLocked(row.pkg, checked) }
        holder.itemView.setOnClickListener { holder.toggle.toggle() }
    }
}
