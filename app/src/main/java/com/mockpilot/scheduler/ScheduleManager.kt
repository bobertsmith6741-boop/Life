package com.mockpilot.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mockpilot.data.ScheduleDao
import com.mockpilot.data.ScheduleEntity
import com.mockpilot.data.SessionStore
import com.mockpilot.engine.SpoofManager
import com.mockpilot.model.EngineMode
import com.mockpilot.model.MockConfig
import com.mockpilot.model.TeleportPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the schedule feature. Evaluation is exact-alarm-driven for punctual transitions and backed
 * by a periodic WorkManager job as a safety net (in case an alarm is dropped in deep Doze). Each
 * time it runs it (1) finds the schedule active *now*, (2) starts/moves/stops the session
 * accordingly — transitions between chained places use the TRAVEL teleport policy so they look like
 * a real commute — and (3) arms the next exact alarm at the next start/end boundary.
 */
@Singleton
class ScheduleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val spoofManager: SpoofManager,
    private val sessionStore: SessionStore,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Kick off planning immediately and register the periodic safety worker. */
    fun sync() {
        val periodic = PeriodicWorkRequestBuilder<SchedulePlannerWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_WORK, ExistingPeriodicWorkPolicy.UPDATE, periodic,
        )
        SchedulePlannerWorker.enqueueNow(context)
    }

    /** Core evaluation. Safe to call repeatedly (from an alarm, worker, or app start). */
    suspend fun applyPlan() {
        val schedules = scheduleDao.allEnabled()
        val now = Calendar.getInstance()

        val active = activeScheduleAt(schedules, now)
        if (active != null) {
            spoofManager.retarget(active.toConfig())
            sessionStore.startedByScheduler = true
        } else if (sessionStore.startedByScheduler) {
            // The scheduler previously owned the session and nothing is active now → stand down.
            spoofManager.stop()
            sessionStore.startedByScheduler = false
        }

        nextBoundaryAfter(schedules, now)?.let { armAlarm(it) }
    }

    // ---- Scheduling math (pure, so it can be reasoned about / tested) ----

    private fun activeScheduleAt(schedules: List<ScheduleEntity>, now: Calendar): ScheduleEntity? {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val todayBit = weekdayBit(now)
        val yesterdayBit = weekdayBit((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) })

        // Prefer the latest-starting active schedule so chained entries override earlier ones.
        return schedules.filter { s ->
            val today = (s.daysMask and todayBit) != 0
            val yesterday = (s.daysMask and yesterdayBit) != 0
            when {
                s.endMinuteOfDay > s.startMinuteOfDay ->
                    today && minute >= s.startMinuteOfDay && minute < s.endMinuteOfDay
                s.endMinuteOfDay < s.startMinuteOfDay -> // window wraps past midnight
                    (today && minute >= s.startMinuteOfDay) || (yesterday && minute < s.endMinuteOfDay)
                else -> false
            }
        }.maxByOrNull { it.startMinuteOfDay }
    }

    /** The next start-or-end boundary strictly after [now], searched across the coming week. */
    private fun nextBoundaryAfter(schedules: List<ScheduleEntity>, now: Calendar): Long? {
        if (schedules.isEmpty()) return null
        var best: Long? = null
        for (dayOffset in 0..7) {
            val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val bit = weekdayBit(day)
            for (s in schedules) {
                if (s.daysMask and bit == 0) continue
                for (minuteOfDay in intArrayOf(s.startMinuteOfDay, s.endMinuteOfDay)) {
                    val t = (day.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
                        set(Calendar.MINUTE, minuteOfDay % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (t > now.timeInMillis && (best == null || t < best!!)) best = t
                }
            }
        }
        return best
    }

    private fun weekdayBit(cal: Calendar): Int {
        // Calendar.MONDAY=2 … SUNDAY=1 → bit 0=Mon … bit 6=Sun.
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val index = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        return 1 shl index
    }

    private fun armAlarm(triggerAtMillis: Long) {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_FIRE)
        val pi = PendingIntent.getBroadcast(
            context, ALARM_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Fall back to an inexact-but-doze-friendly alarm if exact isn't permitted.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm denied; using inexact", se)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        private const val TAG = "ScheduleManager"
        private const val ALARM_REQUEST = 90210
        private const val SAFETY_WORK = "schedule_safety_planner"
    }
}

private fun ScheduleEntity.toConfig(): MockConfig = MockConfig(
    mode = EngineMode.STATIONARY,
    target = toGeoPoint(),
    teleportPolicy = TeleportPolicy.TRAVEL,
    nightIdleEnabled = true,
    label = "$label ($placeName)",
)
