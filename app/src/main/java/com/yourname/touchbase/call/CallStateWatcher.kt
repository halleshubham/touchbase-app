package com.yourname.touchbase.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager

/**
 * Watches for an OFFHOOK -> IDLE transition, i.e. "a call just ended," and
 * fires [onCallEnded] once. Meant to be registered right before firing
 * ACTION_CALL and unregistered as soon as it fires or the screen leaves
 * composition — see CallQueueScreen's DisposableEffect.
 */
class CallStateWatcher(private val onCallEnded: () -> Unit) {

    private var wasOffHook = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_OFFHOOK -> wasOffHook = true
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    if (wasOffHook) {
                        wasOffHook = false
                        onCallEnded()
                    }
                }
            }
        }
    }

    fun register(context: Context) {
        context.registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
    }

    fun unregister(context: Context) {
        runCatching { context.unregisterReceiver(receiver) }
    }
}
