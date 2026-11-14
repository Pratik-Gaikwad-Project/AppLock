package com.example.applock

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores the PIN (salted PBKDF2 hash only), locked-app list and settings. */
class PinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)

    // ---- PIN ----

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val actual = hash(pin, Base64.decode(salt, Base64.NO_WRAP))
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 50_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ---- Locked apps ----

    fun lockedApps(): Set<String> = prefs.getStringSet(KEY_LOCKED, emptySet()) ?: emptySet()

    fun isLocked(pkg: String): Boolean = lockedApps().contains(pkg)

    fun setLocked(pkg: String, locked: Boolean) {
        val set = HashSet(lockedApps())
        if (locked) set.add(pkg) else set.remove(pkg)
        prefs.edit().putStringSet(KEY_LOCKED, set).apply()
    }

    // ---- Settings / state ----

    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION, false)
        set(v) = prefs.edit().putBoolean(KEY_PROTECTION, v).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIO, false)
        set(v) = prefs.edit().putBoolean(KEY_BIO, v).apply()

    var failCount: Int
        get() = prefs.getInt(KEY_FAILS, 0)
        set(v) = prefs.edit().putInt(KEY_FAILS, v).apply()

    var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT, 0L)
        set(v) = prefs.edit().putLong(KEY_LOCKOUT, v).apply()

    private companion object {
        const val KEY_HASH = "pin_hash"
        const val KEY_SALT = "pin_salt"
        const val KEY_LOCKED = "locked_apps"
        const val KEY_PROTECTION = "protection_enabled"
        const val KEY_BIO = "biometric_enabled"
        const val KEY_FAILS = "fail_count"
        const val KEY_LOCKOUT = "lockout_until"
    }
}
