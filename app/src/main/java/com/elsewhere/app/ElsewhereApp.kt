package com.elsewhere.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.elsewhere.app.crew.CrewPublisher
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.map.Sdk
import com.elsewhere.app.sonar.SonarEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ElsewhereApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var hub: RoomHub
    @Inject lateinit var publisher: CrewPublisher
    @Inject lateinit var engine: SonarEngine
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Sdk.init(this)
        // Friend rooms, the share publisher and Sonar outlive any screen: a ping must land while the app is backgrounded.
        hub.start(); publisher.start(); engine.start()
    }
}
