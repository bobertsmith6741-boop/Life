package com.mockpilot.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Woken by the exact alarm armed in [ScheduleManager]. Hands off to a WorkManager job so the actual
 * (async, DB-touching) planning runs durably rather than in the tight BroadcastReceiver window.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE) {
            SchedulePlannerWorker.enqueueNow(context.applicationContext)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mockpilot.action.SCHEDULE_FIRE"
    }
}
