package com.mockpilot

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfig
import javax.inject.Inject

@HiltAndroidApp
class MockPilotApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // osmdroid: identify ourselves to the OSM tile servers and cache under our data dir.
        OsmConfig.getInstance().apply {
            userAgentValue = packageName
            load(this@MockPilotApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
