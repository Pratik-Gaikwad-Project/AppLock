package com.example.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts protection after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val store = PinStore(context)
            if (store.protectionEnabled && store.hasPin()) {
                LockService.start(context)
            }
        }
    }
}
