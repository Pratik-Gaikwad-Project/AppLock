package com.example.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that watches which app is in front and shows the lock
 * screen when a locked app comes to the foreground.
 */
class LockService : Service() {

    companion object {
        /** Package the user has just unlocked; cleared when they leave that app. */
        @Volatile
        var unlockedPkg: String? = null

        private const val CHANNEL_ID = "applock_protection"
        private const val NOTIF_ID = 1
        private const val POLL_MS = 300L
        private const val RELAUNCH_GUARD_MS = 1500L

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LockService::class.java))
        }
    }

    private lateinit var store: PinStore
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastLaunch = 0L

    private val poll = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Exception) {
                // Keep the loop alive no matter what.
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PinStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun tick() {
        foregroundPackage()?.let { current = it }
        val pkg = current ?: return

        // Our own screens and system UI never trigger or reset anything.
        if (pkg == packageName || pkg == "com.android.systemui") return

        // User has left the app they unlocked -> it must be unlocked again next time.
        if (pkg != unlockedPkg) unlockedPkg = null

        if (store.isLocked(pkg) && pkg != unlockedPkg) showLock(pkg)
    }

    @Suppress("DEPRECATION")
    private fun foregroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5_000, now)
        val e = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) latest = e.packageName
        }
        return latest
    }

    private fun showLock(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < RELAUNCH_GUARD_MS) return
        lastLaunch = now
        val intent = Intent(this, LockActivity::class.java)
            .putExtra(LockActivity.EXTRA_PKG, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "App protection", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while AppLock is protecting your apps" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_app)
            .setContentTitle("AppLock is active")
            .setContentText("Your selected apps are protected")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
