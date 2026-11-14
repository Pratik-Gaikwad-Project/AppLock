package com.example.applock

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/**
 * PIN / fingerprint screen. Used in two modes:
 *  - target = a locked app (launched by LockService)
 *  - target = our own package (guards the AppLock settings screen; returns a result)
 */
class LockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PKG = "target_pkg"
        private const val MAX_PIN = 8
        private const val MAX_FAILS = 5
        private const val LOCKOUT_MS = 30_000L
    }

    private lateinit var store: PinStore
    private lateinit var targetPkg: String
    private lateinit var dots: TextView
    private lateinit var hint: TextView
    private val entered = StringBuilder()
    private var countdown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_lock)

        store = PinStore(this)
        targetPkg = intent.getStringExtra(EXTRA_PKG) ?: packageName
        dots = findViewById(R.id.lockDots)
        hint = findViewById(R.id.lockHint)

        bindTargetInfo()
        buildKeypad()
        updateDots()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (targetPkg == packageName) {
                    setResult(RESULT_CANCELED)
                    finish()
                } else {
                    goHome()
                }
            }
        })

        if (inLockout()) startCountdown()

        val bioButton = findViewById<Button>(R.id.btnBiometric)
        if (store.biometricEnabled && canUseBiometric()) {
            bioButton.visibility = android.view.View.VISIBLE
            bioButton.setOnClickListener { showBiometric() }
            if (!inLockout()) showBiometric()
        }
    }

    override fun onDestroy() {
        countdown?.cancel()
        super.onDestroy()
    }

    private fun bindTargetInfo() {
        val title = findViewById<TextView>(R.id.lockTitle)
        val icon = findViewById<ImageView>(R.id.lockIcon)
        if (targetPkg == packageName) {
            title.text = getString(R.string.app_name)
            icon.setImageResource(R.drawable.ic_lock_app)
            return
        }
        try {
            val pm = packageManager
            title.text = pm.getApplicationLabel(pm.getApplicationInfo(targetPkg, 0))
            icon.setImageDrawable(pm.getApplicationIcon(targetPkg))
        } catch (_: Exception) {
            title.text = targetPkg
            icon.setImageResource(R.drawable.ic_lock_app)
        }
    }

    private fun buildKeypad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        val grid = findViewById<GridLayout>(R.id.keypad)
        for (k in keys) {
            val b = Button(this).apply {
                text = k
                textSize = 24f
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(36).toFloat()
                    setColor(0xFF2A2A2A.toInt())
                }
                setOnClickListener { onKey(k) }
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(72)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            grid.addView(b, lp)
        }
    }

    private fun onKey(k: String) {
        if (inLockout()) return
        when (k) {
            "⌫" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
            "✓" -> {
                submit()
                return
            }
            else -> if (entered.length < MAX_PIN) entered.append(k)
        }
        updateDots()
    }

    private fun updateDots() {
        dots.text = if (entered.isEmpty()) " " else entered.map { "●" }.joinToString(" ")
    }

    private fun submit() {
        val pin = entered.toString()
        entered.clear()
        updateDots()
        if (pin.isEmpty()) return

        if (store.verify(pin)) {
            store.failCount = 0
            unlock()
            return
        }
        val fails = store.failCount + 1
        if (fails >= MAX_FAILS) {
            store.failCount = 0
            store.lockoutUntil = System.currentTimeMillis() + LOCKOUT_MS
            startCountdown()
        } else {
            store.failCount = fails
            hint.text = "Wrong PIN ($fails/$MAX_FAILS)"
        }
    }

    private fun inLockout(): Boolean = System.currentTimeMillis() < store.lockoutUntil

    private fun startCountdown() {
        countdown?.cancel()
        val remaining = store.lockoutUntil - System.currentTimeMillis()
        if (remaining <= 0) return
        countdown = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                hint.text = "Too many attempts. Try again in ${ms / 1000 + 1}s"
            }

            override fun onFinish() {
                hint.text = "Enter PIN"
            }
        }.start()
    }

    private fun unlock() {
        if (targetPkg != packageName) LockService.unlockedPkg = targetPkg
        setResult(RESULT_OK)
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun canUseBiometric(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometric() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock")
            .setSubtitle(findViewById<TextView>(R.id.lockTitle).text)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }
}
