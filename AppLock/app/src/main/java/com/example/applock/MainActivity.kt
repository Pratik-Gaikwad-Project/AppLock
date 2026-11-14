package com.example.applock

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    companion object {
        /** True while the user has passed the PIN check for this visit to the app. */
        @Volatile
        var authed = false
    }

    private lateinit var store: PinStore
    private lateinit var rowUsage: TextView
    private lateinit var rowOverlay: TextView
    private lateinit var rowNotif: TextView
    private lateinit var btnUsage: MaterialButton
    private lateinit var btnOverlay: MaterialButton
    private lateinit var btnNotif: MaterialButton
    private lateinit var btnPin: MaterialButton
    private lateinit var btnApps: MaterialButton
    private lateinit var btnToggle: MaterialButton
    private lateinit var switchBio: SwitchMaterial
    private lateinit var txtStatus: TextView

    private var awaitingAuth = false
    private var internalNav = false

    private val authLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            awaitingAuth = false
            if (result.resultCode == RESULT_OK) authed = true else finish()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PinStore(this)

        rowUsage = findViewById(R.id.rowUsage)
        rowOverlay = findViewById(R.id.rowOverlay)
        rowNotif = findViewById(R.id.rowNotif)
        btnUsage = findViewById(R.id.btnUsage)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnNotif = findViewById(R.id.btnNotif)
        btnPin = findViewById(R.id.btnPin)
        btnApps = findViewById(R.id.btnApps)
        btnToggle = findViewById(R.id.btnToggle)
        switchBio = findViewById(R.id.switchBio)
        txtStatus = findViewById(R.id.txtStatus)

        btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        btnPin.setOnClickListener { showPinFlow() }
        btnApps.setOnClickListener {
            if (!store.hasPin()) {
                toast("Set a PIN first")
            } else {
                internalNav = true
                startActivity(Intent(this, AppListActivity::class.java))
            }
        }
        btnToggle.setOnClickListener { toggleProtection() }

        switchBio.setOnCheckedChangeListener { button, checked ->
            if (!button.isPressed) return@setOnCheckedChangeListener
            if (checked) {
                val can = BiometricManager.from(this)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                    toast("No fingerprint enrolled on this device")
                    switchBio.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            store.biometricEnabled = checked
        }
    }

    override fun onResume() {
        super.onResume()
        internalNav = false
        if (store.hasPin() && !authed && !awaitingAuth) {
            awaitingAuth = true
            authLauncher.launch(
                Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_PKG, packageName)
            )
            return
        }
        refresh()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !awaitingAuth && !internalNav) authed = false
    }

    private fun refresh() {
        val usage = Perms.hasUsageAccess(this)
        val overlay = Perms.hasOverlay(this)
        val notif = Perms.hasNotifications(this)

        rowUsage.text = (if (usage) "✓ " else "✗ ") + "Usage access"
        rowOverlay.text = (if (overlay) "✓ " else "✗ ") + "Display over other apps"
        rowNotif.text = (if (notif) "✓ " else "✗ ") + "Notifications"
        btnUsage.isEnabled = !usage
        btnOverlay.isEnabled = !overlay
        btnNotif.isEnabled = !notif

        btnPin.text = if (store.hasPin()) "Change PIN" else "Set PIN"
        switchBio.isChecked = store.biometricEnabled
        btnToggle.text = if (store.protectionEnabled) "Stop protection" else "Start protection"

        val lockedCount = store.lockedApps().size
        txtStatus.text = if (store.protectionEnabled)
            "Protection is ON · $lockedCount app(s) locked"
        else
            "Protection is OFF · $lockedCount app(s) selected"
    }

    private fun toggleProtection() {
        if (store.protectionEnabled) {
            store.protectionEnabled = false
            LockService.stop(this)
        } else {
            when {
                !store.hasPin() -> toast("Set a PIN first")
                !Perms.hasUsageAccess(this) || !Perms.hasOverlay(this) ->
                    toast("Grant usage access and overlay permission first")
                else -> {
                    store.protectionEnabled = true
                    LockService.start(this)
                }
            }
        }
        refresh()
    }

    // ---- PIN dialogs ----

    private fun showPinFlow() {
        if (store.hasPin()) {
            askPin("Enter current PIN") { current ->
                if (store.verify(current)) askNewPin() else toast("Wrong PIN")
            }
        } else {
            askNewPin()
        }
    }

    private fun askNewPin() {
        askPin("New PIN (4–8 digits)") { first ->
            if (first.length !in 4..8) {
                toast("PIN must be 4–8 digits")
                return@askPin
            }
            askPin("Confirm new PIN") { second ->
                if (first == second) {
                    store.setPin(first)
                    toast("PIN saved")
                    authed = true
                    refresh()
                } else {
                    toast("PINs don't match")
                }
            }
        }
    }

    private fun askPin(title: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
