package com.mockpilot.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mockpilot.data.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Restarts an interrupted spoofing session after a reboot (or an app update). If the last session
 * was active, its persisted [com.mockpilot.model.MockConfig] is restored into the [EngineController]
 * and the foreground service is relaunched.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var controller: EngineController

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> maybeResume(context)
        }
    }

    private fun maybeResume(context: Context) {
        if (!sessionStore.autoResume) return
        val config = sessionStore.loadConfig() ?: return
        controller.setDesired(config)
        MockLocationService.start(context)
    }
}
