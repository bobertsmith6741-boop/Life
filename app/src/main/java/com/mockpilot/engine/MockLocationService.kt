package com.mockpilot.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mockpilot.R
import com.mockpilot.model.EngineState
import com.mockpilot.model.LocationSample
import com.mockpilot.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground service that runs the emission loop for the lifetime of a spoofing session. Uses a
 * partial [PowerManager.WakeLock] so the 1 Hz cadence survives Doze, and a persistent notification
 * (FOREGROUND_SERVICE_LOCATION) as required on modern Android.
 */
@AndroidEntryPoint
class MockLocationService : Service() {

    @Inject lateinit var controller: EngineController
    @Inject lateinit var engine: MockLocationEngine

    // Single-threaded confinement: the engine's mutable state is touched by both the emission loop
    // and the config collector, so serialize all engine access onto one thread.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> startEngine()
        }
        return START_STICKY
    }

    private fun startEngine() {
        if (loopJob?.isActive == true) return

        startForegroundCompat(buildNotification("Starting…"))
        acquireWakeLock()

        when (val result = engine.startProviders()) {
            is TestProviderManager.Result.Denied -> {
                controller.publishState(EngineState.Error("Not selected as mock location app"))
                updateNotification("Permission needed — see Setup")
                // Keep the service up briefly so the UI can read the error, then stop.
                scope.launch {
                    delay(1500)
                    stopEverything()
                }
                return
            }
            TestProviderManager.Result.Ok -> Unit
        }

        controller.markStarted(System.currentTimeMillis())

        // Feed config changes into the engine.
        scope.launch {
            controller.desiredConfig.filterNotNull().collect { engine.applyConfig(it) }
        }

        // The emission loop. Engine returns the delay until the next tick (overnight idle slows it).
        loopJob = scope.launch {
            controller.desiredConfig.value?.let { engine.applyConfig(it) }
            while (isActive) {
                val nextDelay = engine.tick()
                maybeRefreshNotification()
                delay(nextDelay)
            }
        }
    }

    private fun stopEverything() {
        loopJob?.cancel()
        loopJob = null
        engine.stop()
        controller.markStopped()
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        engine.stop()
        controller.markStopped()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Notification ----

    private var lastNotifUpdate = 0L

    private fun maybeRefreshNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifUpdate < 1000) return
        lastNotifUpdate = now
        updateNotification(statusLine(controller.lastSample.value, controller.state.value))
    }

    private fun statusLine(sample: LocationSample?, state: EngineState): String {
        val where = sample?.let {
            String.format(Locale.US, "%.5f, %.5f · ±%.0fm · %.1f m/s",
                it.point.latitude, it.point.longitude, it.accuracyM, it.speedMps)
        } ?: "Acquiring…"
        val phase = when (state) {
            is EngineState.GpsLost -> "GPS lost (${state.remainingSeconds}s)"
            is EngineState.Traveling -> "Traveling (${state.remainingMeters.toInt()}m left)"
            is EngineState.Running -> state.subMode
            is EngineState.Error -> "Error: ${state.message}"
            EngineState.Stopped -> "Stopped"
        }
        return "$phase · $where"
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notif_channel_desc) }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ---- WakeLock ----

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.mockpilot.action.START"
        const val ACTION_STOP = "com.mockpilot.action.STOP"

        private const val CHANNEL_ID = "mock_location"
        private const val NOTIF_ID = 4211
        private const val WAKELOCK_TAG = "MockPilot::EngineWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 12L * 60 * 60 * 1000 // 12h safety cap

        fun start(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MockLocationService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
