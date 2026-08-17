package com.mockpilot.xposed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives detection reports broadcast from hooked processes and appends them to the module app's
 * local log, viewable in [com.mockpilot.xposed.ui.DetectionLogActivity].
 */
class DetectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScopeStore.ACTION_DETECTION) return
        val pkg = intent.getStringExtra("pkg") ?: return
        val method = intent.getStringExtra("method") ?: return
        val time = intent.getLongExtra("time", System.currentTimeMillis())
        ScopeStore.appendDetection(context.applicationContext, pkg, method, time)
    }
}
