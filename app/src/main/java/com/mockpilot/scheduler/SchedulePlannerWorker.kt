package com.mockpilot.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Durable executor for schedule evaluation. AlarmManager wakes us punctually; the actual planning
 * (which touches Room and the engine) runs here so it survives process death and Doze deferral.
 */
@HiltWorker
class SchedulePlannerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scheduleManager: ScheduleManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            scheduleManager.applyPlan()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "schedule_planner_now"

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SchedulePlannerWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
