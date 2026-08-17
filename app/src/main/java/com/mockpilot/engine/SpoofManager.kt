package com.mockpilot.engine

import android.content.Context
import com.mockpilot.data.SessionStore
import com.mockpilot.model.MockConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one entry point the UI (and scheduler) use to start/stop spoofing. Wraps the split
 * responsibilities — desired config in [EngineController], persistence in [SessionStore], and the
 * foreground [MockLocationService] — behind two calls.
 */
@Singleton
class SpoofManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: EngineController,
    private val sessionStore: SessionStore,
) {
    val controllerRef: EngineController get() = controller

    /** Begin (or retarget) a spoofing session. Persisted so a reboot resumes it. */
    fun start(config: MockConfig) {
        controller.setDesired(config)
        sessionStore.saveConfig(config)
        MockLocationService.start(context)
    }

    /** Update the target of a live session without a teleport jump policy change. */
    fun retarget(config: MockConfig) {
        controller.setDesired(config)
        sessionStore.saveConfig(config)
        if (!controller.isRunning) MockLocationService.start(context)
    }

    fun stop() {
        sessionStore.clear()
        MockLocationService.stop(context)
    }
}
